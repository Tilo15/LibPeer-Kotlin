import libpeer.discoverers.Samband
import libpeer.formats.BinaryAddress
import libpeer.networks.Ipv4
import kotlin.concurrent.thread

fun main() {

    // Create the network
    val network = Ipv4()
    network.goUp()

    // Set up the discoverer
    val discoverer = Samband(listOf(network))

    // Listen for discoveries
    discoverer.discovered.subscribe {
        println("Found peer " + it.address.toString())
    }

    discoverer.start()


    while (true) {
        // Read a message
        val appNamespace = readLine()!!

        // Is the message a "#" ?
        if (appNamespace == "#") {
            break
        }

        discoverer.addApplication(appNamespace.toByteArray())

        thread {
            while(true) {
                var delay = 0
                discoverer.getAddresses().forEach {
                    // TODO this should be cleaner
                    val addr = BinaryAddress(it.networkType, it.networkAddress, it.networkPort, appNamespace.toByteArray())
                    delay = discoverer.advertise(addr)
                }

                Thread.sleep(delay.toLong())

            }
        }
    }

    discoverer.stop()
    network.goDown()
}