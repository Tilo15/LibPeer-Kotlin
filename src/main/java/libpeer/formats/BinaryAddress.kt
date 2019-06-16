package libpeer.formats

import libpeer.util.DeserialisationSegment
import kotlin.text.Charsets.UTF_8

class BinaryAddress(
    val networkType: ByteArray,
    val networkAddress: ByteArray,
    val networkPort: ByteArray,
    val application: ByteArray = ByteArray(0),
    val label: ByteArray = ByteArray(0)
) {

    init {
        // Error if invalid label
        if(label.isNotEmpty() && label.size != 32) {
            throw Exception("Binary address label must either be of length 0, or 32")
        }
    }

    override fun toString(): String {
        // Start the formatting
        var output = "${networkType.toString(UTF_8)}["

        // Do we have an application?
        if(application.isNotEmpty()) {
            output += "${application.toString(UTF_8)}://"
        }


        // Add address and port
        output += "${networkAddress.toString(UTF_8)}:${networkPort.toString(UTF_8)}"

        // Do we have a label?
        if(label.isNotEmpty()) {
            output += "/${label.toString(UTF_8)}"
        }

        // Return human readable address
        return "$output]"
    }

    fun serialise(): ByteArray {

        // Calculate buffer size to allocate
        var size = application.size + label.size + networkType.size + networkAddress.size + networkPort.size + 5


        // Create buffer
        val buffer = ByteArray(size)

        // Keep track of where we are in the buffer
        var position = 0

        buffer[position] = 0x01
        position ++

        application.copyInto(buffer, position)
        position += application.size

        if(label.isEmpty()){
            buffer[position] = 0x02
            position ++
        }
        else{
            buffer[position] = 0x2F
            position ++

            label.copyInto(buffer, position)
            position += label.size
        }

        networkType.copyInto(buffer, position)
        position += networkType.size

        buffer[position] = 0x1F
        position ++

        networkAddress.copyInto(buffer, position)
        position += networkAddress.size

        buffer[position] = 0x1F
        position ++

        networkPort.copyInto(buffer, position)
        position += networkPort.size

        buffer[position] = 0x04

        // Return the buffer
        return buffer

    }

    companion object {
        fun deserialise(buffer: ByteArray): BinaryAddress {

            if(buffer[0] != 0x01.toByte()){
                throw IllegalArgumentException("Passed ByteArray does not represent a valid BinaryAddress")
            }

            val labelDelimiter = buffer.indexOf(0x2F)
            val networkDelimiter = buffer.indexOf(0x02)

            val hasLabel = labelDelimiter != -1

            val delimiter = if(hasLabel) labelDelimiter else networkDelimiter

            // Get the application namespace
            val application = ByteArray(delimiter -1)
            buffer.copyInto(application, 0, 1, delimiter)

            // Calculate the position to start at
            var position = application.size + 2

            // Somewhere to hold the label
            var label: ByteArray

            // Does it have a label?
            if(hasLabel) {
                // Get ready to hold the label
                label = ByteArray(32)

                // Copy into label
                buffer.copyInto(label, 0, position, position + 32)

                // Increase position
                position += 32
            }
            else {
                // Zero length label
                label = ByteArray(0)
            }

            // Read network field
            val networkSegment = DeserialisationSegment(buffer, 0x1F, position)

            // Read address field
            val addressSegment = networkSegment.nextSegement(0x1F)

            // Read the port field
            val portSegment = addressSegment.nextSegement(0x04)

            // Return a new binary address with this data
            return BinaryAddress(
                networkSegment.segment,
                addressSegment.segment,
                portSegment.segment,
                application,
                label
            )

        }
    }

    override fun hashCode(): Int {
        val hashable = networkType + networkAddress + networkPort
        return hashable.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if(other is BinaryAddress) {
            return networkType.contentEquals(other.networkType) &&
                    networkAddress.contentEquals(other.networkAddress) &&
                    networkPort.contentEquals(other.networkPort)

        }

        return false
    }

}