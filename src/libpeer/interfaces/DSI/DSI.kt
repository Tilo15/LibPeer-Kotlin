package libpeer.interfaces.DSI

import io.reactivex.subjects.PublishSubject
import libpeer.application.Application
import libpeer.formats.BinaryAddress
import libpeer.formats.Reception
import libpeer.interfaces.BaseInterface
import libpeer.transports.DSTP.DSTP
import libpeer.transports.Transports
import java.io.IOException

class DSI(application: Application, channel: ByteArray) :
    BaseInterface(application, channel, Transports.TRANSPORT_DSTP) {

    private val connections: HashMap<BinaryAddress, Connection> = HashMap()

    val newConnection: PublishSubject<Connection> = PublishSubject.create()

    override fun receive(reception: Reception) {
        // Do we have an existing connection with this peer?
        if(connections.containsKey(reception.peer)) {
            // Pass the message along
            connections[reception.peer]!!.receive(reception.data)
        }
        else{
            // Create a new connection object for this peer
            val connection = Connection({rawSend(it, reception.peer)}, {newConnection.onNext(it)}, reception.peer )

            // Save the connection
            connections[reception.peer] = connection

            // Process the first lot of data
            connection.receive(reception.data)
        }
    }

    fun connect(peer: BinaryAddress, timeout: Int = 20000): Connection {
        if(connections.containsKey(peer) && connections[peer]!!.connected){
            throw IOException("Active DSI connection with specified peer already exists over this channel")
        }

        val connection = Connection({rawSend(it, peer)}, {newConnection.onNext(it)}, peer)
        connections[peer] = connection

        connection.connect()
        connection.waitConnected(timeout)

        return connection
    }
}