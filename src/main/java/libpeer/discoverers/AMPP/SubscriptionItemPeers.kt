package libpeer.discoverers.AMPP

import libpeer.formats.AMPP.Subscription
import libpeer.formats.BinaryAddress
import libpeer.util.HashableSequence
import java.util.*
import kotlin.collections.HashSet

class SubscriptionItemPeers(val appId: HashableSequence) {
    val peers: HashSet<BinaryAddress> = HashSet()

    fun update(address: BinaryAddress, subscription: Subscription) {
        if(subscription.subscriptions.contains(appId)) {
            peers.add(address)
        }
    }

    val hasPeers: Boolean get () {
        return peers.size > 0
    }
}