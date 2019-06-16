import libpeer.formats.BinaryAddress
import libpeer.networks.Ipv4
import kotlin.text.Charsets.UTF_8

fun main() {

    // Create the network
    val network = Ipv4(hashMapOf(
        "address" to "127.0.0.1",
        "port" to "3000"
    ))

    // Put the network up
    network.goUp()

    // Listen for data
    network.incoming.subscribe {
        println("Message from ${it.address}: ${it.data.toString(UTF_8)}")

        // Print hex of binary address
        for (b in it.address.serialise()) {
            val st = String.format("%02X", b)
            print(st)
        }
        print("\n")

        println(BinaryAddress.deserialise(it.address.serialise()).toString())
    }

    while (true) {
        // Read a message
        val message = readLine()!!

        // Is the message a "#" ?
        if(message == "#") {
            break
        }

        // Construct address to send to
        val address = BinaryAddress(network.identifier, "127.0.0.1".toByteArray(), "3000".toByteArray())

        println("Sending message...")

        // Send the message
        network.send(message.toByteArray(), address).subscribe {
            println("Message sent!")
        }

    }

    // Put network down
    network.goDown()
}