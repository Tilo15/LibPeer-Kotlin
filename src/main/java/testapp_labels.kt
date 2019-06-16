import libpeer.application.standalone.StandaloneApplication
import kotlin.text.Charsets.UTF_8

fun main() {
    val app = StandaloneApplication("labeltest".toByteArray(UTF_8))

    app.addLabel("labelabelabelabelabelabelabelabe".toByteArray())
    app.addLabel("labelabelabelabelabelabelabelabl".toByteArray())
    app.setDiscoverable(true)

    while (true) {
        println("\n----")
        for (peer in app.findPeers()) {
            print("${peer.address} (last seen: ${System.currentTimeMillis() - peer.lastSeen}ms ago)\n    ")
            println(peer.labels.joinToString("\n    "))
        }

        println("There are ${app.findPeersWithLabel("labelabelabelabelabelabelabelabl".toByteArray()).size} peer(s) with a funny label")
        Thread.sleep(5000)
    }
}