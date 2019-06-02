package libpeer.interfaces

import libpeer.application.Application
import libpeer.formats.BinaryAddress
import libpeer.formats.Reception

abstract class BaseInterface(private val application: Application, val channel: ByteArray, private val transportId: Byte) : Interface {
    override fun processReception(reception: Reception) {
        // Only forward onto the implementation if the message is of correct channel and transport
        if(reception.channel.contentEquals(channel) && reception.transport == transportId) {
            receive(reception)
        }
    }

    protected abstract fun receive(reception: Reception)

    protected fun rawSend(data: ByteArray, peer: BinaryAddress) {
        application.send(data, transportId, peer, channel)
    }
}