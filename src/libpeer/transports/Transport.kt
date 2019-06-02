package libpeer.transports

import io.reactivex.Observable
import io.reactivex.subjects.Subject
import libpeer.formats.BinaryAddress
import libpeer.formats.Receipt
import libpeer.formats.TransportPacket

interface Transport {

    val identifier: Byte
    val incoming: Subject<TransportPacket>

    fun send(data: ByteArray, channel: ByteArray, address: BinaryAddress): Observable<Receipt>

}