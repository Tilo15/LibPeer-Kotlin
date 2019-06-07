package libpeer.interfaces.SODI

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import libpeer.application.Application
import libpeer.formats.BinaryAddress
import libpeer.formats.Reception
import libpeer.interfaces.BaseInterface
import libpeer.transports.Transports
import libpeer.util.HashableSequence
import libpeer.util.toByteArray
import libpeer.util.toHashableSequence
import java.lang.Exception
import java.util.*

class SODI(application: Application, channel: ByteArray = ByteArray(16)) :
    BaseInterface(application, channel, Transports.TRANSPORT_DSTP) {

    private val replies: HashMap<BinaryAddress, Reply> = HashMap()
    private val tokens: HashMap<BinaryAddress, ByteArray> = HashMap()
    private val solicitations: HashMap<HashableSequence, PublishSubject<Reply>> = HashMap()

    val solicited: PublishSubject<Solicitation> = PublishSubject.create()


    override fun receive(reception: Reception) {
        // If a reply already exists, pass data into it
        if(replies.containsKey(reception.peer)) {
            val reply = replies[reception.peer]!!

            // Get the object state before passing in data
            val objState = reply.hasObject

            // Pass in data
            reply.receive(reception.data)

            // Handle events for this reply
            checkComplete(reply, reception.peer, objState)
        }
        else if(tokens.containsKey(reception.peer)) {
            // Create a reply object
            val reply = Reply(reception.peer, reception.data)

            // If the token matches, save it
            if(reply.token.toByteArray().contentEquals(tokens[reception.peer]!!)) {
                replies[reception.peer] = reply

                // Handle events for this reply
                checkComplete(reply, reception.peer)
            }
        }

        else {
            try {
                // Must be a solicitation!
                val solic = Solicitation.deserialise(reception.data, reception.peer) { data, peer -> rawSend(data, peer)}

                // Notify app
                solicited.onNext(solic)
            }
            catch (e: Exception) {
                // Nothing
            }
        }
    }

    private fun checkComplete(reply: Reply, peer: BinaryAddress, priorHasObjectSate: Boolean = false) {
        // If done, forget about it all!
        if(reply.isComplete) {
            tokens.remove(peer)
            replies.remove(peer)
        }

        // If that changed, notify app
        if(priorHasObjectSate != reply.hasObject) {
            solicitations[reply.token.toHashableSequence()]!!.onNext(reply)
            solicitations[reply.token.toHashableSequence()]!!.onComplete()
            solicitations.remove(reply.token.toHashableSequence())
        }
    }

    private fun solicit(peer: BinaryAddress, query: String): Observable<Reply> {
        val solic = Solicitation(query, UUID.randomUUID(), peer) { data, peer -> rawSend(data, peer)}

        // Create and store the observable
        val observable = PublishSubject.create<Reply>()
        solicitations[solic.token.toHashableSequence()] = observable

        // Store the token so we can accept the response
        tokens[peer] = solic.token.toByteArray()

        // Send the solicitation
        rawSend(solic.serialise(), peer)

        // Return observable
        return observable
    }



}