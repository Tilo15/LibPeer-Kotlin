package libpeer.application.standalone

import libpeer.application.Peer
import libpeer.formats.BinaryAddress
import libpeer.util.HashableSequence
import libpeer.util.toHashableSequence

class StandalonePeer(val address: BinaryAddress, var lastSeen: Long = System.currentTimeMillis(), val labels: HashSet<HashableSequence> = hashSetOf()) : Peer {

    fun seen(label: ByteArray) {
        if(!label.isEmpty()) {
            labels.add(label.toHashableSequence())
        }

        lastSeen = System.currentTimeMillis()
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return address == other
    }

}