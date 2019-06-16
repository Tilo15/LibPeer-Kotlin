package libpeer.interfaces.OMI

import libpeer.util.toByteArray
import libpeer.util.uuidOf
import org.msgpack.MessagePack
import org.msgpack.template.Template
import org.msgpack.type.Value
import java.nio.ByteBuffer
import java.util.*

class ObjectMessage(obj: Any?, val ttl: Byte = 8, val id: UUID = UUID.randomUUID()) {
    private var payloadSize: Int = 0
    private var payload: ByteArray = ByteArray(0)

    var isReady: Boolean = false
    private val messagePack: MessagePack = MessagePack()

    fun receive(data: ByteArray): ByteArray {

        val maxRead = payloadSize - payload.size
        val read = Math.min(data.size - 1, maxRead)

        payload += data.sliceArray(IntRange(0, read))

        if(payloadSize == payload.size) {
            this.isReady = true
        }

        return data.sliceArray(IntRange(read + 1, data.size - 1))
    }

    fun getObject(): Value {
        val messagePack = MessagePack()
        return messagePack.read(payload)
    }

    fun <T>getObject(template: Template<T>): T {
        val messagePack = MessagePack()
        return messagePack.read(payload, template)
    }

    init {
        if(obj != null){
            payload = messagePack.write(obj)
            payloadSize = payload.size
            isReady = true
        }
    }

    companion object {
        fun deserialise(data: ByteArray): ObjectMessage {

            val dataBuffer = ByteBuffer.wrap(data, 0, 6)

            // Data length
            val size = dataBuffer.getInt()

            // TTL
            val ttl = dataBuffer.getChar().toByte()

            // Message Id
            val uuid = uuidOf(data.sliceArray(IntRange(5, 21)))

            // Create object
            val om = ObjectMessage(null, ttl, uuid)
            om.payloadSize = size

            om.receive(data.sliceArray(IntRange(21, data.size - 1)))

            return om
        }
    }

    fun serialise(): ByteArray {
        val lengthTtl = ByteBuffer.allocate(5).putInt(payloadSize).put((ttl.toInt() - 1).toByte()).array()
        return lengthTtl + id.toByteArray() + payload
    }
}