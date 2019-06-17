package libpeer.discoverers.AMPP

import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import libpeer.discoverers.AMPP.bootstrappers.Bootstrapper
import libpeer.discoverers.AMPP.bootstrappers.DNS
import libpeer.discoverers.AMPP.bootstrappers.Ipv4Multicast
import libpeer.discoverers.Discoverer
import libpeer.formats.AMPP.Advertorial
import libpeer.formats.AMPP.Subscription
import libpeer.formats.BinaryAddress
import libpeer.formats.Discovery
import libpeer.formats.NetworkPacket
import libpeer.networks.Network
import libpeer.util.HashableSequence
import libpeer.util.toHashableSequence
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.text.Charsets.UTF_8

class AMPP(networks: List<Network>) : Discoverer {
    override val discovered: Subject<Discovery> = PublishSubject.create()

    private val applications: HashSet<HashableSequence> = HashSet()
    private val peersAddressQueried: HashSet<BinaryAddress> = HashSet()
    private val reportedAddresses: HashSet<BinaryAddress> = HashSet()

    private val instanceId: UUID = UUID.randomUUID()
    private val hashableInstanceId: HashableSequence = instanceId.toHashableSequence()

    private val peers: HashSet<BinaryAddress> = HashSet()
    private val subscriptionItemPeers: HashMap<HashableSequence, SubscriptionItemPeers> = HashMap()

    private val cache: HashSet<Advertorial> = HashSet()
    private var resubscribe: Boolean = true
    private val subscriptionIds: HashSet<HashableSequence> = HashSet()

    private val networkMap: Map<HashableSequence, Network> = networks.associate { Pair(it.identifier.toHashableSequence(), it) }

    private val bootstrappers: HashSet<Bootstrapper> = hashSetOf(DNS(), Ipv4Multicast(networks))

    init {
        // Intialise AMPP subscription item peers
        subscriptionItemPeers[NAMESPACE] = SubscriptionItemPeers(NAMESPACE)

        for(network in networks) {
            network.incoming.subscribe {
                networkIncoming(it)
            }
        }

        for(bootstrapper in bootstrappers) {
            bootstrapper.discovered.subscribe {
                newAmppPeer(it)
            }
        }
    }

    companion object {
        const val CACHE_LIMIT = 10000
        val HEADER = "AMPP".toByteArray(UTF_8)
        val NAMESPACE = "AMPP".toByteArray(UTF_8).toHashableSequence()
        val MESSAGE_ADVERTORIAL = "ADV".toByteArray(UTF_8).toHashableSequence()
        val MESSAGE_SUBSCRIPTION = "SUB".toByteArray(UTF_8).toHashableSequence()
        val MESSAGE_ADDRESS_QUERY = "ADQ".toByteArray(UTF_8).toHashableSequence()
        val MESSAGE_ADDRESS_RESPONSE = "ADR".toByteArray(UTF_8).toHashableSequence()
    }

    override fun advertise(address: BinaryAddress): Int {
        // Create the advertorial
        val advertorial = Advertorial(address, 30, 180)

        // Cache the advertorial to immediately send to new peers
        addToCache(advertorial)

        // Keep track of the number of peers that the advertorial was sent to
        var sendCount = 0

        // Are there any interested peers?
        val namespace = address.application.toHashableSequence()
        if(subscriptionItemPeers.containsKey(namespace)) {
            // Send to all interested peers
            for(peer in subscriptionItemPeers[namespace]!!.peers) {
                send(MESSAGE_ADVERTORIAL.byteArray + advertorial.serialise(), peer)
                // Only keep track of non "AMPP" advertorials
                if(namespace != NAMESPACE) {
                    sendCount ++
                }
            }
        }

        // While we're at it, update our subscriptions too
        sendSubscriptions(peers)

        // If we aren't already advertising our AMPP node, do it now
        if(namespace != NAMESPACE) {
            advertise(BinaryAddress(address.networkType, address.networkAddress, address.networkPort, NAMESPACE.byteArray))
        }

        // Did we manage to send to any peers?
        if(sendCount > 0) {
            // Tell the application to re-advertise in 180 seconds (the timeout)
            return 180000
        }

        // If we didn't actually send our advertorial to any peers, tell the application to try again in 5 seconds.
        return 5000
    }

    override fun addApplication(namespace: ByteArray) {
        applications.add(namespace.toHashableSequence())
        resubscribe = true
        sendSubscriptions(peers)
    }

    override fun removeApplication(namespace: ByteArray) {
        applications.remove(namespace.toHashableSequence())
    }

    override fun getAddresses(): List<BinaryAddress> {
        return reportedAddresses.toList()
    }

    override fun start() {
        bootstrappers.forEach {
            it.start()
        }
    }

    override fun stop() {
        bootstrappers.forEach {
            it.stop()
        }
    }

    private fun newAmppPeer(address: BinaryAddress){
        // Do we already have this peer?
        if(!peers.contains(address)) {
            // Add the peer
            peers.add(address)

            // Autosubscribe it to AMPP
            subscriptionItemPeers[NAMESPACE]!!.peers.add(address)

            // Send it our subscriptions
            resubscribe = true
            sendSubscriptions(hashSetOf(address))

            // Send it an address query
            peersAddressQueried.add(address)
            send(MESSAGE_ADDRESS_QUERY.byteArray, address)

            // Advertise our known AMPP peers to it
            for (peer in peers) {
                send(MESSAGE_ADVERTORIAL.byteArray + Advertorial(address, 1, 280).serialise(), peer)
            }
        }
    }

    private fun sendSubscriptions(peers: HashSet<BinaryAddress>) {
        // Create the subscription
        val subscription = Subscription(applications, !resubscribe)

        // Set resubscription status
        resubscribe = false

        // Send to all AMPP peers
        for (peer in peers) {
            send(MESSAGE_SUBSCRIPTION.byteArray + subscription.serialise(), peer)
        }
    }



    private fun networkIncoming(networkPacket: NetworkPacket) {
        // Is this an AMPP packet?
        if(networkPacket.data.sliceArray(IntRange(0, 3)).contentEquals(HEADER)) {
            // This is a message from an AMPP peer, get it's instance ID
            val instance = networkPacket.data.sliceArray(IntRange(4, 19)).toHashableSequence()

            if(instance == hashableInstanceId) {
                // Ignore if we are getting our own packet
                return
            }

            // If we are talking to someone new, add them
            val amppAddress = BinaryAddress(
                networkPacket.address.networkType,
                networkPacket.address.networkAddress,
                networkPacket.address.networkPort,
                NAMESPACE.byteArray)
            newAmppPeer(amppAddress)

            // Get message type
            val messageType = networkPacket.data.sliceArray(IntRange(20, 22)).toHashableSequence()
            val message = networkPacket.data.sliceArray(IntRange(23, networkPacket.data.size - 1))

            when(messageType) {
                MESSAGE_ADVERTORIAL -> handleAdvetorial(message, networkPacket.address)
                MESSAGE_SUBSCRIPTION -> handleSubscription(message, networkPacket.address)
                MESSAGE_ADDRESS_QUERY -> handleAddressQuery(message, networkPacket.address)
                MESSAGE_ADDRESS_RESPONSE -> handleAddressResponse(message, networkPacket.address)
            }
        }
    }

    private fun handleAdvetorial(message: ByteArray, address: BinaryAddress) {
        val advertorial = Advertorial.deserialise(message)

        // Have we received this before?
        if(!cache.contains(advertorial)) {
            // Do we have peers interesting in this app?
            val namespace = advertorial.address.application.toHashableSequence()
            if(subscriptionItemPeers.containsKey(namespace)) {
                // Can we forward it?
                if(advertorial.ttl > 0){
                    // Forward to those peers!
                    for (peer in subscriptionItemPeers[namespace]!!.peers) {
                        // Make sure we don't return the packet to the sending peer
                        if(peer != address) {
                            send(MESSAGE_ADVERTORIAL.byteArray + advertorial.serialise(), peer)
                        }
                    }
                }
            }

            // Are *we* interested in it?
            if(applications.contains(namespace)) {
                // Let the application know
                // TODO calculate useful AD
                discovered.onNext(Discovery(advertorial.address, 10))
            }

            // Is this an AMPP advertorial?
            if(namespace == NAMESPACE) {
                // Add it as a peer!
                newAmppPeer(advertorial.address)
            }

            // Add to the cache
            if(advertorial.ttl > 0) {
                addToCache(advertorial)
            }
        }
    }

    private fun handleSubscription(message: ByteArray, address: BinaryAddress) {
        val subscription = Subscription.deserialise(message)

        val subscriptionId = subscription.id.toHashableSequence()

        // Have we received this before?
        if(!subscriptionIds.contains(subscriptionId)) {
            // Add it to the set of received subscription messages
            subscriptionIds.add(subscriptionId)

            // If they aren't already, subscribe them to AMPP discoveries
            subscription.subscriptions.add(NAMESPACE)

            // Have they subscribed to something we don't know of yet?
            for(namespace in subscription.subscriptions) {
                if(!subscriptionItemPeers.containsKey(namespace)) {
                    subscriptionItemPeers[namespace] = SubscriptionItemPeers(namespace)
                }
            }

            // Add/Remove sender from Subscription
            for (subscriptionItem in subscriptionItemPeers.values) {
                subscriptionItem.update(address, subscription)
            }

            // Do they want to see cached items?
            if(!subscription.renewing) {
                // Send applicable cached items
                getFromCache(subscription.subscriptions).forEach {
                    send(MESSAGE_ADVERTORIAL.byteArray + it.serialise(), address)
                }

                // Send to all AMPP peers
                for(peer in peers) {
                    // Don't send to the sending peer
                    if(peer != address){
                        send(MESSAGE_SUBSCRIPTION.byteArray + subscription.serialise(), peer)
                    }
                }
            }
        }
    }

    private fun handleAddressQuery(message: ByteArray, address: BinaryAddress) {
        // Create new binary address
        val replyAddress = BinaryAddress(
            address.networkType,
            address.networkAddress,
            address.networkPort
        )

        // Reply immediately
        send(MESSAGE_ADDRESS_RESPONSE.byteArray + replyAddress.serialise(), address)
    }

    private fun handleAddressResponse(message: ByteArray, address: BinaryAddress) {
        // Did we ask this peer for an address?
        if(peersAddressQueried.contains(address)) {
            // Deserialise the address
            val response = BinaryAddress.deserialise(message)

            // Save the response
            reportedAddresses.add(response)

            // Remove peer from list of queried peers
            peersAddressQueried.remove(address)
        }
    }

    private fun send(data: ByteArray, address: BinaryAddress) {
        // Do we have that network?
        val networkId = address.networkType.toHashableSequence()
        if(networkMap.containsKey(networkId)) {
            val network = networkMap[networkId]!!
            network.send(HEADER + hashableInstanceId.byteArray + data, address).subscribe {
                // TODO handle errors? log?
            }
        }
        else {
            throw  IOException("Network type unavailable")
        }
    }

    private fun cleanCache() {
        // Remove all expired advertorials
        val expired = cache.filter { it.expired }
        cache.removeAll(expired)
    }

    private fun addToCache(advertorial: Advertorial) {
        // Clean up cache (might give us more room)
        cleanCache()

        // Do we have room?
        if(cache.size < CACHE_LIMIT) {
            // Add it
            cache.add(advertorial)
        }
    }

    private fun getFromCache(namespaces: HashSet<HashableSequence>): List<Advertorial> {
        // Clean cache before returning items
        cleanCache()

        // TODO optimise
        return cache.filter { namespaces.contains(it.address.application.toHashableSequence()) }
    }
}