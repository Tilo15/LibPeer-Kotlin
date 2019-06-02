package libpeer.application

import libpeer.formats.BinaryAddress
import libpeer.util.HashableSequence

interface Peer {
    val address: BinaryAddress
    var lastSeen: Long
    val labels: HashSet<HashableSequence>

    fun seen(label: ByteArray)
}