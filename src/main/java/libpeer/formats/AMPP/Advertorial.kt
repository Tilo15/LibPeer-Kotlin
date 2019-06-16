package libpeer.formats.AMPP

import libpeer.formats.BinaryAddress
import libpeer.util.HashableSequence
import libpeer.util.toByteArray
import libpeer.util.toHashableSequence
import libpeer.util.uuidOf
import org.msgpack.MessagePack
import org.msgpack.template.Templates
import java.util.*
import kotlin.collections.HashMap
import kotlin.text.Charsets.UTF_8

class Advertorial(val address: BinaryAddress, val ttl: Int, val expiresIn: Long, val id: UUID = UUID.randomUUID()) {
    val received: Long = System.currentTimeMillis()

    val hashableId: HashableSequence = id.toHashableSequence()

    companion object {
        val FIELD_ADDRESS = "address".toByteArray(UTF_8)
        val FIELD_TTL = "ttl".toByteArray(UTF_8)
        val FIELD_EXPIRES_IN = "expires_in".toByteArray(UTF_8)
        val FIELD_ID = "id".toByteArray(UTF_8)

        private val messagePack = MessagePack()

        fun deserialise(data: ByteArray): Advertorial {
            val obj = messagePack.read(data, Templates.tMap(Templates.TByteArray, Templates.TValue))

            val keyAddress = obj.keys.find { it.contentEquals(FIELD_ADDRESS) }
            val keyTtl = obj.keys.find { it.contentEquals(FIELD_TTL) }
            val keyExpiresIn = obj.keys.find { it.contentEquals(FIELD_EXPIRES_IN) }
            val keyId = obj.keys.find { it.contentEquals(FIELD_ID) }

            val address = BinaryAddress.deserialise(messagePack.convert(obj[keyAddress], Templates.TByteArray))
            val ttl = messagePack.convert(obj[keyTtl], Templates.TInteger)
            val expiresIn = messagePack.convert(obj[keyExpiresIn], Templates.TLong)
            val id = uuidOf(messagePack.convert(obj[keyId], Templates.TByteArray))

            return Advertorial(address, ttl, expiresIn, id)
        }
    }

    fun serialise(): ByteArray{
        val obj = HashMap<ByteArray, Any>()
        obj[FIELD_ADDRESS] = address.serialise()
        obj[FIELD_TTL] = ttl
        obj[FIELD_EXPIRES_IN] = expiresIn
        obj[FIELD_ID] = id.toByteArray()

        return messagePack.write(obj)
    }

    val expired: Boolean get() {
        return System.currentTimeMillis() > received + (expiresIn * 1000)
    }

    override fun hashCode(): Int {
        return hashableId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if(other is Advertorial) {
            return hashableId == other.hashableId
        }
        return false
    }
}