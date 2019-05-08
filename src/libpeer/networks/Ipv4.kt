package libpeer.networks

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import libpeer.formats.BinaryAddress
import libpeer.formats.NetworkPacket
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.nio.charset.Charset
import kotlin.concurrent.thread
import kotlin.text.Charsets.UTF_8

class Ipv4(override val options: HashMap<String, String> = HashMap()) : Network {

    override val identifier: ByteArray = "IPv4".toByteArray(UTF_8)
    override var up: Boolean = false
    override val incoming: Subject<NetworkPacket> = PublishSubject.create<NetworkPacket>()
    private var socket: DatagramSocket? = null

    override fun goUp(): Boolean {
        socket = when {
            "address" in options -> {
                val port = options["port"]!!.toInt()
                val address = Inet4Address.getByName(options["address"])
                DatagramSocket(port, address)
            }
            "port" in options -> {
                val port = options["port"]!!.toInt()
                DatagramSocket(port)
            }
            else -> DatagramSocket()
        }

        thread {
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
    override fun send(data: ByteArray, address: BinaryAddress): Observable<Boolean> {
        // Is the network up?
        if(!up) {
            throw IOException("Cannot send data when network is down")
        }

        return Observable.create<Boolean> {
            // Get the port number
            val port = address.networkPort.toString(UTF_8).toInt()

            // Get the IP address
            val ipAddress = Inet4Address.getByName(address.networkAddress.toString(UTF_8))

            // Create the packet
            val packet = DatagramPacket(data, data.size, ipAddress, port)

            // Send the packet
            socket!!.send(packet)

            // Report success to observer
            it.onNext(true)

            // Complete
            it.onComplete()
        }
    }

    fun getAddress(): BinaryAddress {
        return BinaryAddress(identifier,
            this.socket!!.inetAddress.hostAddress.toByteArray(UTF_8),
            this.socket!!.port.toString().toByteArray(UTF_8))
    }
}