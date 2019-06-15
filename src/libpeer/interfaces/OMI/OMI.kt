package libpeer.interfaces.OMI

import io.reactivex.subjects.PublishSubject
import libpeer.application.Application
import libpeer.formats.BinaryAddress
import libpeer.formats.Reception
import libpeer.interfaces.BaseInterface
import libpeer.transports.Transports
import java.io.IOException

class OMI(application: Application, channel: ByteArray = ByteArray(16)) :
    BaseInterface(application, channel, Transports.TRANSPORT_DSTP) {

    val newMessage: PublishSubject<ObjectMessageReception> = PublishSubject.create()

    private val pending: HashMap<BinaryAddress, ObjectMessage> = HashMap()

    override fun receive(reception: Reception) {
        val message: ObjectMessage

        // Are we in the process of receiving a message from this peer?
        if(pending.containsKey(reception.peer)) {
            // Get the message
            message = pending[reception.peer]!!

            // Pass in the data
            message.receive(reception.data) // TODO this outputs data it didn't use, recycle it
        }
        else {
            // No, start new message
            message = ObjectMessage.deserialise(reception.data)

            // Save into pending
            pending[reception.peer] = message
        }

        // Is this message ready?
        if(message.isReady) {
            // Remove from pending
            pending.remove(reception.peer)

            // Notify application
            newMessage.onNext(ObjectMessageReception(message, reception.peer))
        }
    }

    fun send(objectMessage: ObjectMessage, peer: BinaryAddress) {
        if(objectMessage.ttl <= 0) {
            throw IOException("Cannot send Object Message with TTL <= 0")
        }
        rawSend(objectMessage.serialise(), peer)
    }




}