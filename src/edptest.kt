import libpeer.discoverers.Samband
import libpeer.formats.BinaryAddress
import libpeer.muxer.Muxer
import libpeer.networks.Ipv4
import libpeer.transports.EDP
import kotlin.text.Charsets.UTF_8

fun main() {
    val app = "helloworld".toByteArray(UTF_8)

    val net = Ipv4()
    net.goUp()

    val muxer = Muxer(listOf(net))
    muxer.addApplication(app)

    val peers: HashSet<BinaryAddress> = HashSet()

    val disc = Samband(listOf(net))
    disc.addApplication(app)
    disc.discovered.subscribe {
        println("Found peer ${it.address}")
        peers.add(it.address)
    }
    disc.start()

    val trans = EDP(muxer)
    trans.incoming.subscribe {
        println("Got message from peer ${it.address}: ${it.data.toString(UTF_8)}")
    }

    while (true) {
        // Read a message
        val message = readLine()!!

        // Is the message a "#" ?
        if (message == "#") {
            break
        }

        val messageBytes = message.toByteArray(UTF_8)

        println("************************************************")
        for (peer in peers) {
            trans.send(messageBytes, ByteArray(16), peer).subscribe {
                if(it.success)
                    println("Sent to $peer")
                else
                    println("Failed to send to $peer")
            }
        }

        disc.getAddresses().forEach {
            val addr = BinaryAddress(it.networkType, it.networkAddress, it.networkPort, app)
            disc.advertise(addr)
        }

        println("Advertised")

    }

}