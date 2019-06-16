import libpeer.application.Peer
import libpeer.application.standalone.StandaloneApplication
import libpeer.formats.Discovery
import libpeer.interfaces.DSI.Connection
import libpeer.interfaces.DSI.DSI
import libpeer.interfaces.SODI.Reply
import libpeer.interfaces.SODI.SODI
import org.msgpack.MessagePack
import org.msgpack.template.MessagePackableTemplate
import org.msgpack.template.Templates
import org.msgpack.type.MapValue
import java.io.*
import java.lang.Exception
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.math.roundToInt

fun main() {
    // Create the application
    val app = StandaloneApplication("testftp".toByteArray())

    val sodi = SODI(app)

    sodi.solicited.subscribe {
        if(it.query.startsWith("get ")) {
            try {
                val bin = Files.readAllBytes(File(it.query.substring(4)).toPath())
                val obj = HashMap<String, String>()
                obj["status"] = "OK"
                thread(name = "App Send File") {
                    it.reply(obj, bin)
                }
            }
            catch (e: Exception) {
                val obj = HashMap<String, String>()
                obj["status"] = e.toString()
                it.reply(obj)
            }
        }

        else {
            val files = File("ftp/").listFiles()
            val obj = HashMap<String, Any>()
            obj["motd"] = "Hello, Kotlin!"
            obj["entries"] = files.map { path ->
                val fileObj = HashMap<String, Any>()
                fileObj["path"] = "ftp/" + path.name
                fileObj["size"] = Files.size(path.toPath())

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
        for(i in 0 until discoveries.size){
            println("[$i] ${discoveries[i].address}, last seen: ${System.currentTimeMillis() - discoveries[i].lastSeen} ms ago")
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
            val items = ArrayList<Map<String, Any>>()
            val lock = ReentrantLock(true)
            lock.lock()

            val listingReceived = lock.newCondition()
            val fileReplyReceived = lock.newCondition()

            val msgp = MessagePack()
            println("Requesting listing from ${peer.address}")

            sodi.solicit(peer.address, "listing").subscribe {
                lock.lock()
                val obj = it.getObject(Templates.tMap(Templates.TString, Templates.TValue))

                println("Got response from ${it.peer}")


                println(msgp.convert(obj["motd"], Templates.TString))

                obj["entries"]!!.asArrayValue().forEach { entry ->
                    items.add(MessagePack().convert(entry, Templates.tMap(Templates.TString, Templates.TValue)))
                }

                // Allow process to continue
                listingReceived.signal()
                lock.unlock()
            }

            // Wait for above to complete
            listingReceived.await()

            for(i in 0 until items.size) {
                val item = items[i]
                println("[$i] ${item["size"]} bytes\t${item["path"]}")
            }

            print("Download file# ")
            val fileSelection = readLine()

            // Re request listing
            if(fileSelection.isNullOrBlank()) {
                continue
            }

            // Exit
            if(fileSelection == "#") {
                break
            }

            // Get selected file
            val file = items[fileSelection!!.toInt()]

            println("Requesting file ${file["path"]} from peer ${peer.address}...")

            var reply: Reply? = null

            lock.lock()

            // Download the file
            sodi.solicit(peer.address, "get ${file["path"].toString().replace("\"", "")}").subscribe {
                lock.lock()
                reply = it
                fileReplyReceived.signal()
                lock.unlock()
            }

            // Wait for reply
            fileReplyReceived.await()

            val fileObj = reply!!.getObject(Templates.tMap(Templates.TString, Templates.TString))

            if(fileObj["status"] == "OK") {
                println("Peer accepted request")
                val output = FileOutputStream(File(file["path"].toString().replace("\"", "")).absolutePath)

                var lastPrint = 0L
                while(reply!!.getTransferInformation().receivedFraction != 1.0f) {
                    output.write(reply!!.read())
                    if(lastPrint < System.currentTimeMillis() - 100){
                        print("Downloading ${file["path"]} ${(reply!!.getTransferInformation().receivedFraction * 100).roundToInt()}% complete...\r")
                        lastPrint = System.currentTimeMillis()
                    }
                }

                output.write(reply!!.read())
                output.close()
                println("\n Download complete!")
            }
            else {
                println("Peer rejected request with status: ${fileObj["status"]}")
            }

            lock.unlock() // Why not?

        }

    }


}