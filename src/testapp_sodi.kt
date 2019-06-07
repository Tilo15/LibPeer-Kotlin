import libpeer.application.Peer
import libpeer.application.standalone.StandaloneApplication
import libpeer.formats.Discovery
import libpeer.interfaces.DSI.Connection
import libpeer.interfaces.DSI.DSI
import libpeer.interfaces.SODI.SODI
import java.io.File
import java.lang.Exception
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    // Create the application
    val app = StandaloneApplication("testftp".toByteArray())

    val sodi = SODI(app)

    sodi.solicited.subscribe {
        if(it.query.startsWith("get ")) {
            try {
                val bin = Files.readAllBytes(Path.of(it.query.substring(4)))
                val obj = HashMap<String, String>()
                obj["status"] = "OK"
                it.reply(obj, bin)
            }
            catch (e: Exception) {
                val obj = HashMap<String, String>()
                obj["status"] = e.toString()
                it.reply(obj)
            }
        }

        else {
            val files = Files.list(Path.of("ftp/"))
            val obj = HashMap<String, Any>()
            obj["motd"] = "Hello, Kotlin!"
            obj["entries"] = files.map { path ->
                val fileObj = HashMap<String, Any>()
                fileObj["path"] = "ftp/" + path.fileName
                fileObj["size"] = Files.size(path)

                fileObj
            }

            it.reply(obj)
        }
    }

    app.setDiscoverable(true)

    while (true) {
        // Get list of peers
        val discoveries: List<Peer> = app.findPeers()

        // Display them
        discoveries.forEach {
            println("[${discoveries.indexOf(it)}] ${it.address}, last seen: ${System.currentTimeMillis() - it.lastSeen} ms ago")
        }

        // Ask user which to connect to
        print("Get listing for peer# ")
        val selection = readLine()

        // Search again for peers
        if(selection.isNullOrBlank()) {
            continue
        }

        // Exit
        if(selection == "#") {
            break
        }

        // Get selected peer
        val peer = discoveries[selection.toInt()]

        while (true) {
            val items =
        }

    }


}