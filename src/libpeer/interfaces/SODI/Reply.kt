package libpeer.interfaces.SODI

import libpeer.formats.BinaryAddress
import libpeer.formats.TransferInformation
import libpeer.util.uuidOf
import org.msgpack.MessagePack
import org.msgpack.type.Value
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class Reply(val peer: BinaryAddress, data: ByteArray) {

    var token: UUID = uuidOf(ByteArray(16))
    var hasObject: Boolean = false
    var isComplete: Boolean = false

    private var objectData: ByteArray = ByteArray(0)
    private var objectDataSize: Long = 0L
    private val binaryData: BlockingQueue<ByteArray> = LinkedBlockingQueue()
    private var binaryDataSize: ULong = 0uL
    private var binaryDataReceived: ULong = 0uL
    private var binaryDataRead: ULong = 0uL
    private var receivedBinarySize: Boolean = false

    init {
        // Read header information
        objectDataSize = ByteBuffer.wrap(data).getInt().toUInt().toLong()
        token = uuidOf(data.sliceArray(IntRange(4, 19)))

        // Handle the rest of the data
        // TODO
    }

    fun receive(data: ByteArray): ByteArray {
        if(objectData.size < objectDataSize) {
            // Figure out how much more we need to read
            val dataLeft = objectDataSize - objectData.size

            // Read up to that amount
            val readEnd = Math.min(data.size.toLong(), dataLeft).toInt()
            val objData = data.sliceArray(IntRange(0, readEnd - 1))

            // Catch any leftovers
            val leftovers = data.sliceArray(IntRange(readEnd, data.size -1))

            // Add new data to the buffer
            objectData += objData

            // Run any leftovers through this function
            if(leftovers.isNotEmpty()) {
                hasObject = true
                return receive(leftovers)
            }
        }
        else if(!receivedBinarySize) {
            // Get size of binary data
            binaryDataSize = ByteBuffer.wrap(data).getLong().toULong()

            // Mark size as received
            receivedBinarySize = true

            // Get leftovers
            val leftovers = data.sliceArray(IntRange(Math.min(data.size - 1, 8), data.size -1))

            // Run any leftovers through this function
            if(leftovers.isNotEmpty()) {
                return receive(leftovers)
            }

        }
        else if(binaryDataReceived < binaryDataSize) {
            // Figure out how much more we need to read
            val dataLeft = binaryDataSize - binaryDataReceived

            // Read up to that amount
            val readEnd = Math.min(data.size.toLong(), dataLeft.toLong()).toInt()
            val binData = data.sliceArray(IntRange(0, readEnd - 1))

            // Catch any leftovers
            val leftovers = data.sliceArray(IntRange(readEnd, data.size -1))

            // Update received amount
            binaryDataReceived += binData.size.toULong()

            // Add to queue
            binaryData.put(binData)

            if(binaryDataSize <= binaryDataReceived) {
                isComplete = true
            }

            // Return leftovers
            if(leftovers.isNotEmpty()){
                return leftovers
            }
        }

        return data
    }


    fun getObject(): Value {
        val messagePack = MessagePack()
        return messagePack.read(objectData)
    }

    fun read(): ByteArray {
        var data = ByteArray(0)

        while (binaryData.peek() != null) {
            data += binaryData.poll()
        }

        binaryDataRead += data.size.toULong()
        return data
    }

    fun readAll(): ByteArray {
        var data = ByteArray(0)

        // Calculate amount of data that has to be read before we are done reading
        val expectedSize = (binaryDataSize - binaryDataRead).toInt()

        while(data.size != expectedSize) {
            data += binaryData.poll()
        }

        binaryDataRead += data.size.toULong()
        return data
    }

    fun getTransferInformation(): TransferInformation {
        return TransferInformation(binaryDataSize.toLong(), binaryDataReceived.toLong(), binaryDataRead.toLong())
    }

}