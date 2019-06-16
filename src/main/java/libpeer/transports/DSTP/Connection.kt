package libpeer.transports.DSTP

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import libpeer.formats.BinaryAddress
import libpeer.formats.Chunk
import libpeer.formats.Receipt
import libpeer.formats.TransportPacket
import libpeer.muxer.Muxer
import libpeer.util.HashableSequence
import libpeer.util.toByteArray
import libpeer.util.toHashableSequence
import libpeer.util.uuidOf
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.concurrent.thread
import kotlin.math.round

class Connection(private val muxer: Muxer, private val channel: ByteArray, private val address: BinaryAddress) {

    companion object {
        const val CHUNK_SIZE = 4096

        const val MESSAGE_CONNECTION_REQUEST: Byte = 0x05
        const val MESSAGE_CONNECTION_ACCEPT: Byte = 0x0D
        const val MESSAGE_DISCONNECT: Byte = 0x18
        const val MESSAGE_CONNECTION_RESET: Byte = 0x10
        const val MESSAGE_PING: Byte = 0x50
        const val MESSAGE_PONG: Byte = 0x70
        const val MESSAGE_CHUNK: Byte = 0x02
        const val MESSAGE_CHUNK_ACKNOWLEDGE: Byte = 0x06
        const val MESSAGE_CHUNK_NEGATIVE_ACKNOWLEDGE: Byte = 0x15

    }

    // Metrics
    private var inFlight: Int = 0
    private var windowSize: Float = 16f
    private var lastPacketDelay: Double = 0.0
    private var lastPacketSent: Int = 0

    // States
    private var connected: Boolean = false
    private var lastPong: Double = 0.0
    private var pingRetries: Int = 0

    // Outbound Chunks
    private val inFlightChunks: HashMap<HashableSequence, Chunk> = HashMap()
    private var lastQueuedChunk: UUID = uuidOf(ByteArray(16))
    private val chunkTrackers: HashSet<ChunkTracker> = HashSet()
    private var chunkQueue: Queue<Chunk> = LinkedList()

    // Inbound Chunks
    private var receivedChunkIds: HashSet<HashableSequence> = HashSet()
    private var receivedChunks: HashMap<HashableSequence, Chunk> = HashMap()
    private var currentChunk: HashableSequence = ByteArray(16).toHashableSequence()

    // Observable
    val dataReady: PublishSubject<TransportPacket> = PublishSubject.create()


    private fun calculateWindowSize() {
        // Calculate the next window size
        val delayFactor = 1.0 - lastPacketDelay * 10.0
        val windowFactor = inFlight.toFloat() / windowSize
        val gain = delayFactor.toFloat() * windowFactor

        windowSize += gain

        // Window size may not go below 5
        if(windowSize < 5) {
            windowSize = 5f
        }
    }

    private fun rawSend(data: ByteArray) {
        // Send to the muxer
        muxer.send(data, channel, 0x06, address).subscribe {
            // No further action required
            // Todo handle network-level errors?
        }
    }

    private fun kill(reason: String) {
        // We are no longer connected
        connected = false

        // Clean up
        inFlight = 0
        windowSize = 16f
        lastPacketDelay = 0.0
        lastPacketSent = 0
        lastPong = 0.0
        inFlightChunks.clear()
        lastQueuedChunk = uuidOf(ByteArray(16))
        chunkQueue.clear()
        receivedChunkIds.clear()
        receivedChunks.clear()
        currentChunk = ByteArray(16).toHashableSequence()

        // Notify trackers
        chunkTrackers.forEach {
            it.canceled(reason)
        }

        chunkTrackers.clear()
    }

    private fun connect() {
        // Only connect if not already connected
        if(!connected) {
            // Send a connection request
            rawSend(byteArrayOf(MESSAGE_CONNECTION_REQUEST))
        }

        // If we are still not connected in 10 seconds, kill.
        timeout(10, {!connected}, {kill("Connection request timed out")})
    }

    private fun ping() {
        // Only ping if the connection is active
        if(connected) {
            // Send the ping request
            rawSend(byteArrayOf(MESSAGE_PING))

            // Mark this point in time
            val timeSent = System.currentTimeMillis() / 1000.0

            // Set a timeout
            timeout(5, {lastPong < timeSent}, {pingFailure()}, {ping()})
        }
    }

    private fun pingFailure() {
        // Have we failed more than 10 times?
        if(pingRetries > 10){
            // Consider the connection dead
            kill("Remote peer stopped responding")
            return
        }

        // Increment ping retries
        pingRetries ++

        // Try again
        ping()
    }

    fun send(data: ByteArray): Observable<Receipt> {
        // Break data up into chunks
        val chunkCount = Math.ceil(data.size / CHUNK_SIZE.toDouble()).toInt()
        val chunks = LinkedList<Chunk>()

        for(i in 0 until chunkCount) {
            // Data start index
            val start = i * CHUNK_SIZE

            // Data end index
            val end = Math.min((i + 1) * CHUNK_SIZE, data.size) - 1

            // Create chunk
            val chunk = Chunk.create(data.sliceArray(IntRange(start, end)), lastQueuedChunk)

            // Put chunk in array
            chunks.add(chunk)

            // Update last queued chunk
            lastQueuedChunk = chunk.id
        }

        // Create a chunk tracker for this transaction
        val tracker = ChunkTracker.create(chunks)

        // Att the chunk tracker to the list
        chunkTrackers.add(tracker)

        // Add the chunks to the queue to be sent
        chunks.forEach {
            chunkQueue.add(it)
        }

        // Are we connected?
        if(connected) {
            // Start sending chunks
            sendChunks()
        }
        else{
            // Connect first
            connect()
        }

        // return the subject
        return tracker.sent
    }


    private fun sendChunks() {
        // Calculate a new window size
        calculateWindowSize()

        // Figure out how many packets we can send to fill the window
        var available = round(windowSize - inFlight).toInt()

        // Store how many chunks we resent
        var resentChunks = 0

        // Loop over sent but not acknowledged chunks
        for(chunk in inFlightChunks.values) {
            // If the chunk was sent over (5 seconds + last packet delay_ ago, resend it
            if(chunk.timeSent < (System.currentTimeMillis() / 1000.0) - (lastPacketDelay + 5)) {
                // Resend it
                sendChunk(chunk)

                // Increment resent chunks
                resentChunks ++
            }

            if(resentChunks > available) {
                // Stop sending chunks
                break
            }
        }

        // Recalculate the window space we have left
        available -= resentChunks

        if(available > 0) {
            // Send the next chunks
            for (i in 0 until available) {
                // Do we have more to send?
                if(chunkQueue.isEmpty()) {
                    // Exit loop
                    break
                }

                // Get next chunk in queue
                val chunk = chunkQueue.poll()

                // Add to in flight chunks list
                inFlightChunks[chunk.id.toHashableSequence()] = chunk

                // Increment in flight counter
                inFlight ++

                // Send the chunk
                sendChunk(chunk)
            }
        }
    }

    private fun sendChunk(chunk: Chunk) {
        // Send the chunk
        rawSend(byteArrayOf(MESSAGE_CHUNK) + chunk.serialise())
    }


    // Receive handlers //

    fun receive(data: ByteArray) {
        val messageType = data[0]

        when(messageType) {
            MESSAGE_CONNECTION_REQUEST -> receiveConnectRequest()
            MESSAGE_CONNECTION_ACCEPT -> receiveConnectAccept()
            MESSAGE_DISCONNECT -> receiveDisconnect()
            MESSAGE_CONNECTION_RESET -> receiveConnectionReset()
            MESSAGE_PING -> receivePing()
            MESSAGE_PONG -> receivePong()
            MESSAGE_CHUNK -> receiveChunk(data.sliceArray(IntRange(1, data.size - 1)))
            MESSAGE_CHUNK_ACKNOWLEDGE -> receiveChunkAcknowledge(data.sliceArray(IntRange(1, data.size - 1)))
            MESSAGE_CHUNK_NEGATIVE_ACKNOWLEDGE -> receiveChunkNegativeAcknowledge(data.sliceArray(IntRange(1, data.size - 1)))
        }
    }

    private fun receiveConnectRequest() {
        // If we are not already connected
        if(!connected) {
            // Mark as connected
            connected = true

            // Acknowledge
            rawSend(byteArrayOf(MESSAGE_CONNECTION_ACCEPT))

            // Start pinging
            ping()
        }
        else {
            // Sent in error, send connection reset
            rawSend(byteArrayOf(MESSAGE_CONNECTION_RESET))
        }
    }

    private fun receiveConnectAccept() {
        // Mark as connected
        connected = true

        // Start pinging
        ping()

        // Start sending chunks
        sendChunks()

    }

    private fun receiveDisconnect() {
        // Stop the connection
        kill("The remote peer closed the connection")
    }

    private fun receiveConnectionReset() {
        // Kill the connection
        kill("Connection reset by remote peer")

        // Attempt to reconnect
        connect()
    }

    private fun receivePing() {
        // Are we connected?
        if(connected) {
            // Reply with pong
            rawSend(byteArrayOf(MESSAGE_PONG))
        }
        else {
            // Send disconnect, remote peer in bad state
            rawSend(byteArrayOf(MESSAGE_DISCONNECT))
        }
    }

    private fun receivePong() {
        // Only care if connection is open
        if(connected) {
            // Record the time it was received
            lastPong = System.currentTimeMillis() / 1000.0

            // Clear retries
            pingRetries = 0

            // Transmit any chunks waiting
            sendChunks()
        }
    }

    private fun receiveChunk(data: ByteArray) {
        // If not connected, send disconnect
        if(!connected) {
            rawSend(byteArrayOf(MESSAGE_DISCONNECT))
            return
        }

        // Deserialise the chunk
        val chunk = Chunk.deserialise(data)

        // If the chunk is valid
        if(chunk.isValid) {
            // Send a chunk acknowledgement
            val acknowledgement = byteArrayOf(MESSAGE_CHUNK_ACKNOWLEDGE) +
                    chunk.id.toByteArray() +
                    ByteBuffer.allocate(8).putDouble(chunk.timeSent).array()

            rawSend(acknowledgement)

            // Handle the chunk
            handleChunk(chunk)
        }
        else {
            // Chunk got broken on the way
            rawSend(byteArrayOf(MESSAGE_CHUNK_NEGATIVE_ACKNOWLEDGE) + chunk.id.toByteArray())
        }
    }

    private fun receiveChunkAcknowledge(data: ByteArray) {
        // Read message
        val chunkId = data.sliceArray(IntRange(0, 15)).toHashableSequence()
        val timeSent = ByteBuffer.wrap(data, 16, 8).double

        // Is this chunk still in flight?
        if(inFlightChunks.containsKey(chunkId)) {
            // Notify trackers
            notifyTrackers(chunkId)

            // Remove from in flight list
            inFlightChunks.remove(chunkId)

            // Decrement in flight count
            inFlight --

            // Set last packet delay
            lastPacketDelay = (System.currentTimeMillis() / 1000.0) - timeSent
        }

        // Continue to send chunks
        sendChunks()
    }

    private fun receiveChunkNegativeAcknowledge(data: ByteArray) {
        // Get ID
        val id = data.toHashableSequence()

        // Is the chunk still in flight?
        if(inFlightChunks.containsKey(id)) {
            // Resend the chunk immediately
            sendChunk(inFlightChunks[id]!!)
        }
    }

    // Chunk Rx/Tx Handlers

    private fun handleChunk(chunk: Chunk) {
        // Have we received this chunk before?
        if(receivedChunkIds.contains(chunk.id.toHashableSequence())) {
            return
        }

        // Add the chunk id to the list of received ids
        receivedChunkIds.add(chunk.id.toHashableSequence())

        // Add the chunk to the received hash map using the previous id
        receivedChunks[chunk.previousChunkId.toHashableSequence()] = chunk

        // Collect the largest possible amount of data from the received chunks while still preserving order
        var data = ByteArray(0)

        // See if any complete message can be passed to the application yet
        while(receivedChunks.containsKey(currentChunk)) {
            // Get the next chunk in the chronological sequence
            val nextChunk = receivedChunks[currentChunk]!!

            // Store the data from the chunk
            data += nextChunk.payload

            // Update the current chunk
            currentChunk = nextChunk.id.toHashableSequence()

            // Remove the chunk from memory
            receivedChunks.remove(nextChunk.previousChunkId.toHashableSequence())
        }

        // Did we collect any data?
        if(data.isNotEmpty()) {
            // Notify the application
           dataReady.onNext(TransportPacket(data, channel, address))
        }
    }

    private fun notifyTrackers(chunkId: HashableSequence) {
        // Save a list of complete trackers
        val complete = HashSet<ChunkTracker>()

        // Loop over every chunk tracker
        chunkTrackers.forEach {
            // Notify the tracker with the ID
            it.chunkAcknowledged(chunkId)

            // If the tracker is complete, mark for deletion
            if(it.complete) {
                complete.add(it)
            }
        }

        // Remove all complete trackers
        complete.forEach {
            chunkTrackers.remove(it)
        }
    }


    // Utility
    private fun timeout(duration: Long, condition: () -> Boolean, action: () -> Any, else_action: (() -> Any)? = null) {

        thread(name = "DSTP Timeout Timer ($duration s)") {
            // Wait for the specified duration
            Thread.sleep(duration * 1000)

            // Check the condition
            if(condition()) {
                // Run the action
                action()
            }
            else {
                else_action?.invoke()
            }
        }

    }

}