package libpeer.application.standalone

import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import libpeer.application.Application
import libpeer.application.Peer
import libpeer.discoverers.AMPP.AMPP
import libpeer.discoverers.Discoverer
import libpeer.discoverers.Samband
import libpeer.formats.*
import libpeer.muxer.Muxer
import libpeer.networks.Ipv4
import libpeer.transports.DSTP.DSTP
import libpeer.transports.EDP
import libpeer.transports.Transport
import libpeer.transports.Transports
import libpeer.util.HashableSequence
import libpeer.util.toHashableSequence
import java.io.IOException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class StandaloneApplication(override val namespace: ByteArray) : Application {

    override val incoming: Subject<Reception> = PublishSubject.create()
    override val newPeer: Subject<Peer> = PublishSubject.create()

    private val txQueue: BlockingQueue<() -> Any> = LinkedBlockingQueue()
    private val rxQueue: BlockingQueue<() -> Any> = LinkedBlockingQueue()

    private val ipv4: Ipv4 = Ipv4()
    private val muxer: Muxer = Muxer(listOf(ipv4))
    private val edp: EDP = EDP(muxer)
    private val dstp: DSTP = DSTP(muxer)

    private val labels: HashSet<HashableSequence> = HashSet()
    private val discoveries: HashSet<StandalonePeer> = HashSet()
    private var discoverable: Boolean = false

    private val discovererInstances: List<Discoverer> = listOf(AMPP(listOf(ipv4)), Samband(listOf(ipv4)))

    override val networks: List<String>
        get() = TODO("not implemented")
    override val transports: List<String>
        get() = TODO("not implemented")
    override val discoverers: List<String>
        get() = TODO("not implemented")


    init {
        // Put IP network up
        ipv4.goUp()

        // Start up a thread for running transmit actions
        thread(name = "Application TX Queue") {
            while(true) {
                txQueue.take()()
            }
        }

        // Start up a thread for running receive actions
        thread(name = "Application RX Queue") {
            while(true) {
                rxQueue.take()()
            }
        }

        discovererInstances.forEach {
            it.start()
            it.addApplication(namespace)
            it.discovered.subscribe { discovery ->
                if(discovery.address.application.contentEquals(namespace)) {
                    // We found something interesting
                    val peer = StandalonePeer(discovery.address)
                    peer.seen(discovery.address.label)
                    discoveries.add(peer)
                    newPeer.onNext(peer)
                }
            }
        }

        // Subscribe to network data from the transports
        // XXX this should be more generic
        dstp.incoming.subscribe {
            rxQueue.put {
                incoming.onNext(Reception(it.data, dstp.identifier, it.channel, it.address))
            }
        }

        edp.incoming.subscribe {
            rxQueue.put {
                incoming.onNext(Reception(it.data, edp.identifier, it.channel, it.address))
            }
        }
    }


    private fun <T>runAction(action: Action<T>): T{
        txQueue.put {
            action.run()
        }

        return action.wait()
    }

    override fun send(data: ByteArray, transportId: Byte, peer: BinaryAddress, channel: ByteArray) {
        val transport = getTransport(transportId)!!

        // Get and wait for a receipt
        val receipt = runAction(Action<Receipt>{
            transport.send(data, channel, peer).subscribe{receipt -> it.complete(receipt)}
        })

        if(receipt.success) {
            return
        }
        throw IOException(receipt.toErrorString())
    }

    override fun addLabel(label: ByteArray) {
        if(label.size != 32) {
            throw IOException("Labels must be 32 bytes long")
        }
        labels.add(label.toHashableSequence())
    }

    override fun removeLabel(label: ByteArray) {
        labels.remove(label.toHashableSequence())
    }

    override fun clearLabels() {
        labels.clear()
    }

    override fun setDiscoverable(value: Boolean) {
        if(!discoverable && value) {
            this.discovererInstances.forEach {
                advertise(it)
            }
        }

        discoverable = value
    }

    override fun close() {
        // Clean up
    }

    override fun findPeers(): List<Peer> {
        return discoveries.toList()
    }

    override fun findPeersWithLabel(label: ByteArray): List<Peer> {
        return discoveries.filter { it.labels.contains(label.toHashableSequence()) }
    }


    private fun getTransport(id: Byte): Transport? {
        return when(id) {
            Transports.TRANSPORT_EDP -> edp

            Transports.TRANSPORT_DSTP -> dstp

            else -> null
        }
    }

    private fun advertise(discoverer: Discoverer) {
        val addresses = discoverer.getAddresses()

        var delay: Long = 0

        // Loop over each address
        for(address in addresses) {
            // Create address with application
            val appAddress = BinaryAddress(address.networkType,
                address.networkAddress,
                address.networkPort,
                namespace)

            // Advertise this address
            delay = discoverer.advertise(appAddress).toLong()

            // Advertise this address with each label
            for(label in labels) {
                // Create address with label
                val labeledAddress = BinaryAddress(address.networkType,
                    address.networkAddress,
                    address.networkPort,
                    namespace,
                    label.byteArray)

                // Advertise it
                discoverer.advertise(labeledAddress)
            }
        }

        // After the specified delay, do it again
        thread(name = "Advertiser Timeout") {
            Thread.sleep(delay)
            if(discoverable){
                advertise(discoverer)
            }
        }
    }

}