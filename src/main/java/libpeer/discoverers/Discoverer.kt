package libpeer.discoverers

import io.reactivex.subjects.Subject
import libpeer.formats.BinaryAddress
import libpeer.formats.Discovery
import libpeer.networks.Network

interface Discoverer {

    val discovered: Subject<Discovery>

    fun advertise(address: BinaryAddress): Int
    fun addApplication(namespace: ByteArray)
    fun removeApplication(namespace: ByteArray)
    fun getAddresses(): List<BinaryAddress>
    fun start()
    fun stop()
}