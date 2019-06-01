package libpeer.muxer

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import libpeer.formats.BinaryAddress
import libpeer.formats.NetworkPacket
import libpeer.formats.Parcel
import libpeer.networks.Network
import libpeer.util.HashableSequence
import libpeer.util.toHashableSequence
import java.lang.Exception
import java.util.*

class Muxer (private val networks: List<Network>){

    public val incoming: Subject<Parcel> = PublishSubject.create<Parcel>()
    private val applications: HashSet<HashableSequence> = HashSet()
    private val networkMap: HashMap<HashableSequence, Network> = HashMap()


    init {
        for(network in networks) {
            // Add to network map
            networkMap[network.identifier.toHashableSequence()] = network

            // Subscribe to incoming data
            network.incoming.subscribe {
                receive(it)
            }
        }
    }

    private fun receive(data: NetworkPacket) {
        try {
            // Attempt to deserialise the parcel
            val parcel = Parcel.deserialise(data)

            try {
                incoming.onNext(parcel)
            }
            catch (e: Exception) {
                // TODO handle (log or something)
            }
        }
        catch (e: Exception){

        }
    }

    public fun send(data: ByteArray, channel: ByteArray, transport: Byte, address: BinaryAddress): Observable<Boolean> {
        // Create the parcel
        val parcel = Parcel(UUID.randomUUID(), channel, transport, data, address)

        // Send it over the network
        return networkMap[address.networkType.toHashableSequence()]!!.send(parcel.serialise(), address)
    }

    public fun addApplication(namespace: ByteArray) {
        applications.add(namespace.toHashableSequence())
    }

    public fun removeApplication(namespace: ByteArray) {
        applications.remove(namespace.toHashableSequence())
    }

}