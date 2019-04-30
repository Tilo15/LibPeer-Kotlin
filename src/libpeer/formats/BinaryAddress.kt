package libpeer.formats

import kotlin.text.Charsets.UTF_8

class BinaryAddress(
    val networkType: ByteArray,
    val networkAddress: ByteArray,
    val networkPort: ByteArray,
    val application: ByteArray = ByteArray(0),
    val label: ByteArray = ByteArray(0)
) {

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
        // TODO
        return ByteArray(0)
    }

}