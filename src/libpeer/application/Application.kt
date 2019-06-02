package libpeer.application

import io.reactivex.subjects.Subject
import libpeer.formats.BinaryAddress
import libpeer.formats.Discovery
import libpeer.formats.Reception

interface Application {
    val namespace: ByteArray

    val incoming: Subject<Reception>
    val newPeer: Subject<Discovery>

    val networks: List<String>
    val transports: List<String>
    val discoverers: List<String>

    fun send(data: ByteArray, transport: Byte, peer: BinaryAddress, channel: ByteArray = ByteArray(16))

    fun addLabel(label: ByteArray)

    fun removeLabel(label: ByteArray)

    fun clearLabels()

    fun setDiscoverable(discoverable: Boolean)

    fun close()

    fun findPeers(): List<Discovery>

    fun findPeersWithLabel(label: ByteArray): List<Discovery>

}