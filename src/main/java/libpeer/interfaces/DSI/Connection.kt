package libpeer.interfaces.DSI

import io.reactivex.Observable
import libpeer.formats.BinaryAddress
import libpeer.formats.Reception
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeoutException

class Connection(private val rawSend: (data: ByteArray) -> Any, private val newConnection: (connection: Connection) -> Any, val peer: BinaryAddress) {


    companion object {
        val HEADER: ByteArray = "DSI".toByteArray()
        const val MESSAGE_CONNECTION_REQUEST: Byte = 0x43 // C
        const val MESSAGE_CONNECTION_ACKNOWLEDGE: Byte = 0x41 // A
        const val MESSAGE_DISCONNECT: Byte = 0x44// D
        const val MESSAGE_SEND_DATA: Byte = 0x53 // S
    }

    var connected: Boolean = false

    private var expectedAck: Boolean = false
    private var expectedLeft: ULong = 0u
    private val fifo: BlockingQueue<ByteArray> = LinkedBlockingQueue()
    private var currentHunk: ByteArray = ByteArray(0)


    fun receive(data: ByteArray) {
        if (expectedLeft == 0uL && data.sliceArray(IntRange(0, 2)).contentEquals(Connection.HEADER)) {
            // Message is valid DSI packet

            when (data[3]) {
                Connection.MESSAGE_CONNECTION_REQUEST -> {
                    // Are we connected?
                    if (!connected) {
                        // We are now
                        connected = true
                        rawSend(Connection.HEADER + byteArrayOf(Connection.MESSAGE_CONNECTION_ACKNOWLEDGE))

                        // Inform application
                        newConnection(this)
                    }
                }

                Connection.MESSAGE_CONNECTION_ACKNOWLEDGE -> {
                    // Did we expect this?
                    if (expectedAck) {
                        // Connect acknowledge
                        connected = true
                        expectedAck = false
                    }
                }

                Connection.MESSAGE_DISCONNECT -> {
                    // Disconnected
                    connected = false
                }

                Connection.MESSAGE_SEND_DATA -> {
                    // Are we connected?
                    if (connected) {
                        // Stream data, get expected length
                        expectedLeft = ByteBuffer.wrap(data, 4, 12).getLong().toULong()

                        // Re-process the rest of this message
                        receive(data.sliceArray(IntRange(12, data.size - 1)))
                    }
                }
            }
        }
        else if(expectedLeft > 0uL) {

            // Kind of hating myself for using unsigned longs in the spec
            var readable = data.size
            if(readable.toULong() > expectedLeft){
                readable = expectedLeft.toInt()
            }

            // Get up to the expected amount of data
            val payload = data.sliceArray(IntRange(0, readable - 1))

            // Preserve leftovers
            val leftovers = data.sliceArray(IntRange(readable, data.size - 1))

            // Add the payload to the queue
            fifo.put(payload)

            // Subtract what we got from what is expected
            expectedLeft -= payload.size.toUInt()

            // If we have data left over, process that
            if(leftovers.isNotEmpty()){
                receive(leftovers)
            }

        }
    }

    fun waitConnected(timeout: Int = 20000) {
        val start = System.currentTimeMillis()
        while (!connected) {
            if(System.currentTimeMillis() - start > timeout){
                throw TimeoutException("The peer did not respond")
            }
        }
    }

    fun connect() {
        expectedAck = true
        rawSend(Connection.HEADER + byteArrayOf(Connection.MESSAGE_CONNECTION_REQUEST))
    }


    fun read(count: Int, timeout: Int = 10000): ByteArray {
        if(currentHunk.isNotEmpty()){
            // Read up to the requested amount of data from the hunk
            val data = currentHunk.sliceArray(IntRange(0, Math.min(currentHunk.size - 1, count)))

            // Remove read data from hunk
            currentHunk = currentHunk.sliceArray(IntRange(data.size, currentHunk.size - 1))

            // If there is more data to be read
            if(data.size < count) {
                // Get more data and append to our current data
                return data + read(count - data.size, timeout)
            }

            return data
        }
        // Get the next hunk
        else{
            if(!connected && fifo.isEmpty()) {
                throw IOException("Cannot read from remote peer, connection closed")
            }

            // TODO implement timeout
            currentHunk = fifo.take()
            return read(count, timeout)
        }
    }

    fun send(data: ByteArray) {
        if(!connected) {
            throw IOException("Cannot send to remote peer, connection closed")
        }

        rawSend(Connection.HEADER + byteArrayOf(MESSAGE_SEND_DATA) + ByteBuffer.allocate(8).putLong(data.size.toLong()).array() + data)
    }



}