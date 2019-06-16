package libpeer.formats
import libpeer.util.toByteArray
import libpeer.util.uuidOf
import org.msgpack.MessagePack
import org.msgpack.template.Templates
import java.io.IOException
import java.security.MessageDigest
import java.util.*
import kotlin.collections.ArrayList

class SambandPacket(val id: UUID, val addreess: BinaryAddress) {

    companion object {
        val HEADER: ByteArray = ubyteArrayOf(0xF0u, 0x9Fu, 0x87u, 0xAEu, 0xF0u, 0x9Fu, 0x87u, 0xB8u).toByteArray()

        fun deserialise(data: ByteArray): SambandPacket {
            if(data.slice(IntRange(0, 7)) != HEADER.toList()) {
                throw IOException("Data does not start with Samband 8 byte header")
            }

            val messagePack = MessagePack()
            val payload = messagePack.read(data.sliceArray(IntRange(8, data.size - 1)), Templates.tList(Templates.TByteArray))

            val serialisedId = payload[0]
            val serialisedAddress = payload[1]

            // Compute checksum
            val hasher = MessageDigest.getInstance("SHA-256")
            hasher.update(serialisedId)
            hasher.update(serialisedAddress)
            val checksum = hasher.digest()

            // Does it fit?
            if (!checksum.contentEquals(payload[2])) {
                throw IOException("Invalid checksum")
            }

            // Return new Samband Packet
            return SambandPacket(uuidOf(serialisedId), BinaryAddress.deserialise(serialisedAddress));
        }
    }

    fun serialise(): ByteArray {
        val serialisedAddress = addreess.serialise()
        val serialisedId = id.toByteArray()

        val hasher = MessageDigest.getInstance("SHA-256")
        hasher.update(serialisedId)
        hasher.update(serialisedAddress)
        val checksum = hasher.digest()

        val data = ArrayList<ByteArray>()
        data.add(serialisedId)
        data.add(serialisedAddress)
        data.add(checksum)

        val msgPack = MessagePack()
        val packedData = msgPack.write(data)

        val buffer = ByteArray(packedData.size + 8)
        SambandPacket.HEADER.copyInto(buffer)
        packedData.copyInto(buffer, 8)
        return buffer
    }

    fun toDiscovery(): Discovery {
        // Convert to Discovery object
        return Discovery(addreess, 1)
    }

}