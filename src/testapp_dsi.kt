import libpeer.application.standalone.StandaloneApplication
import libpeer.interfaces.DSI.Connection
import libpeer.interfaces.DSI.DSI
import kotlin.concurrent.thread

fun main() {
    // Create the application
    val app = StandaloneApplication("dsistreamer".toByteArray())

    // Create DSI instance
    val dsi = DSI(app)

    // Store active connections
    val connections = HashSet<Connection>()

    // Save new connections
    dsi.newConnection.subscribe {
        System.err.println("New connection from ${it.peer}")
        pipe(it)
    }

    app.setDiscoverable(true)

    // Wait 10 seconds and attempt connection to all peers
    Thread.sleep(10000)

    System.err.println("Finding peers...")
    for(discovery in app.findPeers()) {
        try {
            System.err.println("Attempting connection with ${discovery.address}")
            val conn = dsi.connect(discovery.address)
            connections.add(conn)
            pipe(conn)
            System.err.println("Established connection with ${conn.peer}")
        }
        catch (e: Exception) {
            System.err.println("Failed to establish connection with ${discovery.address}, $e")
        }
    }

    System.err.println("Now piping...")

    while(true) {
        val data = System.`in`.readNBytes(8192)

        // Send to each subscribed peer
        connections.forEach {
            if(it.connected) {
                it.send(data)
            }
        }
    }

}

fun pipe(connection: Connection) {
    thread {
        while(connection.connected) {
            val data = connection.read(1024)
            System.out.write(data)
            System.out.flush()
        }

        System.err.println("${connection.peer} disconnected")
    }
}