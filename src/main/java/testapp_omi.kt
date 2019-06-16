import libpeer.application.standalone.StandaloneApplication
import libpeer.interfaces.OMI.OMI
import libpeer.interfaces.OMI.ObjectMessage
import libpeer.interfaces.SODI.SODI
import org.msgpack.template.Templates
import java.lang.Exception

fun main() {
    // Create the application
    val app = StandaloneApplication("omichat".toByteArray())

    val omi = OMI(app)

    omi.newMessage.subscribe {
        println("--------------------------------------------------------------------------------")
        val message = it.objectMessage.getObject(Templates.tMap(Templates.TString, Templates.TString))
        println(message["body"])
        println("From: ${message["name"]} at ${it.peer}")
        println()
    }

    app.setDiscoverable(true)

    print("Enter your name: ")
    val name = readLine()

    while (true) {
        println("Type your message at any time")
        val msg = readLine()

        val message = HashMap<String, String>()
        message["name"] = name.toString()
        message["body"] = msg.toString()

        val peers = app.findPeers()
        println("Found ${peers.size} peers to send to")

        val omiMessage = ObjectMessage(message)

        for (peer in peers) {
            try {
                omi.send(omiMessage, peer.address)
                println("Sent to IP ${peer.address}")
            }
            catch (e: Exception) {
                println("Failed to send to ${peer.address}: $e")
            }
        }
    }

}