package libpeer.formats.AMPP

import libpeer.util.HashableSequence
import libpeer.util.toByteArray
import libpeer.util.toHashableSequence
import libpeer.util.uuidOf
import org.msgpack.MessagePack
import org.msgpack.template.Templates
import java.util.*
import kotlin.collections.HashMap
import kotlin.text.Charsets.UTF_8

class Subscription(val subscriptions: HashSet<HashableSequence>, val renewing: Boolean, val id: UUID = UUID.randomUUID()) {

    companion object {
        val FIELD_SUBSCRIPTIONS = "subscriptions".toByteArray(UTF_8)
        val FIELD_ID = "id".toByteArray(UTF_8)
        val FIELD_RENEWING = "renewing".toByteArray(UTF_8)

        private val messagePack: MessagePack = MessagePack()

        fun deserialise(data: ByteArray): Subscription {

            val obj = messagePack.read(data, Templates.tMap(Templates.TByteArray, Templates.TValue))

            val keySubscriptions = obj.keys.find { it.contentEquals(FIELD_SUBSCRIPTIONS) }
            val keyId = obj.keys.find { it.contentEquals(FIELD_ID) }
            val keyRenewing = obj.keys.find { it.contentEquals(FIELD_RENEWING) }

            val subscriptions = messagePack.convert(obj[keySubscriptions], Templates.tList(Templates.TByteArray)).
                    map { it.toHashableSequence() }.toHashSet()

            val id = uuidOf(messagePack.convert(obj[keyId], Templates.TByteArray))

            val renewing = messagePack.convert(obj[keyRenewing], Templates.TBoolean)

            return Subscription(subscriptions, renewing, id)
        }
    }

    fun serialise(): ByteArray {
        val obj = HashMap<ByteArray, Any>()
        obj[FIELD_SUBSCRIPTIONS] = subscriptions.map { it.byteArray }
        obj[FIELD_ID] = id.toByteArray()
        obj[FIELD_RENEWING] = renewing

        return messagePack.write(obj)
    }

}