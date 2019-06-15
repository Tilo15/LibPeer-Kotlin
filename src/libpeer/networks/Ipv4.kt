package libpeer.networks

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import libpeer.formats.BinaryAddress
import libpeer.formats.NetworkPacket
import libpeer.formats.Receipt
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.concurrent.thread
import kotlin.text.Charsets.UTF_8

class Ipv4(override val options: HashMap<String, String> = HashMap()) : Network {

    override val identifier: ByteArray = "IPv4".toByteArray(UTF_8)
    override var up: Boolean = false
    override val incoming: Subject<NetworkPacket> = PublishSubject.create<NetworkPacket>()
    private var socket: DatagramSocket? = null

    override fun goUp(): Boolean {
        var port = 3000
        var address = InetAddress.getLocalHost()

        if("address" in options) {
            address = Inet4Address.getByName(options["address"])
        }

        if("port" in options){
            val port = options["port"]!!.toInt()
        }

        socket = DatagramSocket(port, address)

        thread(name = "IPv4 Network Listener") {
            while (up) {
                // Ready the buffer
                val buffer = ByteArray(65536)

                // Create the packet
                val packet = DatagramPacket(buffer, buffer.size)

                // Receive the next packet
                socket!!.receive(packet)

                // Create a new binary address
                val address = BinaryAddress(identifier,
                    packet.address.hostAddress.toByteArray(UTF_8),
                    packet.port.toString().toByteArray(UTF_8))

                // Slice the packet data to its intended length
                val data = packet.data.sliceArray(IntRange(0, packet.length - 1))

                // Pass the packet to any listeners
                incoming.onNext(NetworkPacket(data, address))

            }
        }

        // Success, probably
        up = true
        return true

    }

    override fun goDown(): Boolean {
        // TODO stop thread
        up = false

        // Success
        return true
    }

    /**
     * Send the specified data to an address
     */
    override fun send(data: ByteArray, address: BinaryAddress): Observable<Receipt> {
        // Is the network up?
        if(!up) {
            throw IOException("Cannot send data when network is down")
        }

        return Observable.create<Receipt> {
            // Get the port number
            val port = address.networkPort.toString(UTF_8).toInt()

            // Get the IP address
            val ipAddress = Inet4Address.getByName(address.networkAddress.toString(UTF_8))

            // Create the packet
            val packet = DatagramPacket(data, data.size, ipAddress, port)

            // Send the packet
            socket!!.send(packet)

            // Report success to observer
            it.onNext(Receipt.success())

            // Complete
            it.onComplete()
        }
    }

    fun getAddress(): BinaryAddress {
        // TODO this doesn't work
        return BinaryAddress(identifier,
            this.socket!!.localAddress.hostAddress.toByteArray(UTF_8),
            this.socket!!.localPort.toString().toByteArray(UTF_8))
    }
}