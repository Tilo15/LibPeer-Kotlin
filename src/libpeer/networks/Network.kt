package libpeer.networks

import io.reactivex.Observable
import io.reactivex.subjects.Subject
import libpeer.formats.BinaryAddress
import libpeer.formats.NetworkPacket

interface Network {

    val identifier: ByteArray
    val options: HashMap<String, String>
    var up: Boolean
    val incoming: Subject<NetworkPacket>

    fun goUp(): Boolean
    fun goDown(): Boolean
    fun send(data: ByteArray, address: BinaryAddress): Observable<Boolean>

}