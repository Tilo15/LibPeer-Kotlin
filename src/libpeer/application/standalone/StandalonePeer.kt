package libpeer.application.standalone

import libpeer.application.Peer
import libpeer.formats.BinaryAddress
import libpeer.util.HashableSequence
import libpeer.util.toHashableSequence

class StandalonePeer(override val address: BinaryAddress, override var lastSeen: Long = System.currentTimeMillis(), override val labels: HashSet<HashableSequence> = hashSetOf()) : Peer {

    override fun seen(label: ByteArray) {
        if(!label.isEmpty()) {
            labels.add(label.toHashableSequence())
        }

        lastSeen = System.currentTimeMillis()
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if(other is StandalonePeer) {
            return address == other.address
        }
        return false
    }

}