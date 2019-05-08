import libpeer.discoverers.Samband
import libpeer.networks.Ipv4

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
    }

    discoverer.stop()
    network.goDown()
}