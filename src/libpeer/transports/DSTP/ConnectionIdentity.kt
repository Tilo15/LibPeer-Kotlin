package libpeer.transports.DSTP

import libpeer.formats.BinaryAddress
import libpeer.util.HashableSequence

class ConnectionIdentity(val channel: ByteArray, val address: BinaryAddress) {

    override fun hashCode(): Int {
        val sequence = address.application + channel + address.networkType + address.networkAddress + address.networkPort
        return sequence.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if(other is ConnectionIdentity) {
            return address == other.address &&
                    channel.contentEquals(other.channel) &&
                    address.application.contentEquals(other.address.application)
        }

        return false
    }

}