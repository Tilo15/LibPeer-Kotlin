package libpeer.transports.DSTP

import io.reactivex.Observable
import libpeer.formats.BinaryAddress
import libpeer.formats.Parcel
import libpeer.formats.Receipt
import libpeer.formats.TransportPacket
import libpeer.muxer.Muxer
import libpeer.transports.BaseTransport

class DSTP(private val muxer: Muxer) : BaseTransport(muxer) {
    override val identifier: Byte = 0x06

    private val connections: HashMap<ConnectionIdentity, Connection> = HashMap()

    override fun receive(parcel: Parcel) {
        // Pass the data along to the connection
        getConnection(parcel.channel, parcel.address).receive(parcel.payload)
    }

    override fun send(data: ByteArray, channel: ByteArray, address: BinaryAddress): Observable<Receipt> {
        // Send the data using a connection
        return getConnection(channel, address).send(data)
    }

    private fun getConnection(channel: ByteArray, address: BinaryAddress): Connection {
        // Create the identity for this connection
        val id = ConnectionIdentity(address, channel)

        if(connections.containsKey(id)) {
            // Get the existing connection
            return connections[id]!!
        }

        // Create a new connection
        val connection = Connection(muxer, channel, address)

        // Add the connection to the dictionary
        connections[id] = connection

        // Subscribe to new data on the connection
        connection.dataReady.subscribe {
            incoming.onNext(it)
        }

        // Return the connection
        return connection
    }
}