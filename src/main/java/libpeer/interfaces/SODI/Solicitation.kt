package libpeer.interfaces.SODI

import libpeer.application.Application
import libpeer.application.Peer
import libpeer.formats.BinaryAddress
import libpeer.util.toByteArray
import libpeer.util.uuidOf
import org.msgpack.MessagePack
import org.msgpack.template.Templates
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.HashMap
import kotlin.text.Charsets.UTF_8

class Solicitation(val query: String, val token: UUID, val peer: BinaryAddress, private val send: (data: ByteArray, peer: BinaryAddress) -> Any) {
    var txFraction: Float = 0f

    fun serialise(): ByteArray {
        val data: HashMap<ByteArray, ByteArray> = HashMap()
        data[FIELD_QUERY] = query.toByteArray(UTF_8)
        data[FIELD_TOKEN] = token.toByteArray()

        val msgPack = MessagePack()
        val serialisedData = msgPack.write(data)
        return ByteBuffer.allocate(2).putShort(serialisedData.size.toUShort().toShort()).array() + serialisedData
    }

    companion object {
        val FIELD_QUERY = "query".toByteArray(UTF_8)
        val FIELD_TOKEN = "token".toByteArray(UTF_8)

        fun deserialise(data: ByteArray, peer: BinaryAddress, send: (data: ByteArray, peer: BinaryAddress) -> Any): Solicitation {
            val msgPack = MessagePack()
            val message = msgPack.read(data.sliceArray(IntRange(2, data.size - 1)), Templates.tMap(Templates.TByteArray, Templates.TByteArray))

            val queryKey = message.keys.find { it!!.contentEquals(FIELD_QUERY) }
            val tokenKey = message.keys.find { it!!.contentEquals(FIELD_TOKEN) }

            return Solicitation(message[queryKey]!!.toString(UTF_8), uuidOf(message[tokenKey]!!), peer, send)
        }

    }

    private fun <T>createReply(obj: T, dataSize: Long): ByteArray {
        val msgPack = MessagePack()
        val objData = msgPack.write(obj)
        return ByteBuffer.allocate(4).putInt(objData.size).array() +
                token.toByteArray() +
                objData +
                ByteBuffer.allocate(8).putLong(dataSize.toULong().toLong()).array()

    }

    fun <T>reply(obj: T, data: ByteArray = ByteArray(0)) {
        // Send along the data
        send(createReply(obj, data.size.toLong()), peer)
        if(data.isNotEmpty()){
            send(data, peer) // Todo accept streams and stuff too
        }
        txFraction = 1f
    }
}