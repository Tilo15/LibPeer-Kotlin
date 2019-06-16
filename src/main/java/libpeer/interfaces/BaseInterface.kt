package libpeer.interfaces

import libpeer.application.Application
import libpeer.formats.BinaryAddress
import libpeer.formats.Reception

abstract class BaseInterface(private val application: Application, val channel: ByteArray = ByteArray(16), private val transportId: Byte) {

    protected abstract fun receive(reception: Reception)

    protected fun rawSend(data: ByteArray, peer: BinaryAddress) {
        application.send(data, transportId, peer, channel)
    }

    init {
        application.incoming.subscribe {
            // Only forward onto the implementation if the message is of correct channel and transport
            if(it.channel.contentEquals(channel) && it.transport == transportId) {
                receive(it)
            }
        }
    }
}