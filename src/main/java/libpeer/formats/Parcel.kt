package libpeer.formats

import libpeer.util.DeserialisationSegment
import libpeer.util.toByteArray
import libpeer.util.uuidOf
import java.io.IOException
import java.util.*

class Parcel(
    public val id: UUID,
    public val channel: ByteArray,
    public val transport: Byte,
    public val payload: ByteArray,
    public val address: BinaryAddress
) {

    fun serialise(): ByteArray {
        // Create the fixed sized part of the format
        val parcelMeta = ByteArray(36)

        // Add header
        HEADER.copyInto(parcelMeta)

        // Add UUID
        id.toByteArray().copyInto(parcelMeta, 3, 0, 16)

        // Add Channel Id
        channel.copyInto(parcelMeta, 19, 0, 16)

        // Add transport protocol
        parcelMeta[35] = transport

        // Build the rest of the parcel
        val parcel = ByteArray(parcelMeta.size + address.application.size + payload.size + 1)
        parcelMeta.copyInto(parcel, 0, 0, 36)

        // Add the application namespace (protocol)
        address.application.copyInto(parcel, 36)

        // Delimiter
        parcel[36 + address.application.size] = 0x02

        // Payload
        payload.copyInto(parcel, 37 + address.application.size)

        // Return parcel data
        return parcel

    }


    companion object {

        val HEADER: ByteArray = ubyteArrayOf(0x4du, 0x58u, 0x52u).toByteArray()

        fun deserialise(packet: NetworkPacket): Parcel {
            val data = packet.data

            // Is it a valid Muxer Parcel?
            if(data.slice(IntRange(0, 2)) != HEADER.toList()) {
                throw IOException("Parcel does not start with 'MXR' header")
            }

            // Get the UUID
            val serialisedId = ByteArray(16)
            data.copyInto(serialisedId, 0, 3, 19)

            // Get the channel
            val channel = ByteArray(16)
            data.copyInto(channel, 0, 19, 35)

            // Get the transport protocol byte
            val transport = data[35]

            // Get the application and the payload
            val applicationSegment = DeserialisationSegment(data, 0x02, 36)
            val payload = applicationSegment.remainder

            // New BinaryAddress with app details
            val newAddress = BinaryAddress(packet.address.networkType, packet.address.networkAddress, packet.address.networkPort, applicationSegment.segment)

            // Return object
            return Parcel(uuidOf(serialisedId), channel, transport, payload, newAddress)

        }

    }


}