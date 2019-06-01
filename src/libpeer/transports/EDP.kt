package libpeer.transports

import io.reactivex.Observable
import libpeer.formats.BinaryAddress
import libpeer.formats.Parcel
import libpeer.formats.TransportPacket
import libpeer.muxer.Muxer

class EDP(private val muxer: Muxer) : BaseTransport(muxer) {
    override val identifier: Byte = 0x01

    override fun receive(parcel: Parcel) {
        incoming.onNext(TransportPacket(parcel.payload, parcel.channel, parcel.address))
    }

    override fun send(data: ByteArray, channel: ByteArray, address: BinaryAddress): Observable<Boolean> {
        return muxer.send(data, channel, identifier, address)
    }

}