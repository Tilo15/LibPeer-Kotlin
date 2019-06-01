package libpeer.transports

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import libpeer.formats.BinaryAddress
import libpeer.formats.Parcel
import libpeer.formats.TransportPacket
import libpeer.muxer.Muxer

abstract class BaseTransport(private val muxer: Muxer) : Transport {

    override val incoming: Subject<TransportPacket> = PublishSubject.create<TransportPacket>()
    abstract override val identifier: Byte

    init {
        muxer.incoming.subscribe {
            // Is this parcel for us?
            if(it.transport == identifier) {
                // Let implementation handle
                receive(it)
            }
        }
    }

    protected abstract fun receive(parcel: Parcel)
    abstract override fun send(data: ByteArray, channel: ByteArray, address: BinaryAddress): Observable<Boolean>

}