package libpeer.discoverers

import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import libpeer.formats.BinaryAddress
import libpeer.formats.Discovery
import libpeer.formats.SambandPacket
import libpeer.networks.Ipv4
import libpeer.networks.Network
import libpeer.util.HashableSequence
import libpeer.util.toHashableSequence
import java.io.IOException
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.MulticastSocket
import java.util.*
import kotlin.concurrent.thread

class Samband(private val networks: List<Network>) : Discoverer{

    private var ipv4: Ipv4? = null
    private val applications: HashSet<HashableSequence> = HashSet()
    private val receivedIds: HashSet<HashableSequence> = HashSet()
    private var socket: MulticastSocket = MulticastSocket(1944)
    private var up: Boolean = false

    override val discovered: Subject<Discovery> = PublishSubject.create<Discovery>()

    override fun advertise(address: BinaryAddress): Int {
        // Construct the packet
        val packet = SambandPacket(UUID.randomUUID(), address)

        // Serialise
        val data = packet.serialise()

        // Send it
        socket!!.send(DatagramPacket(data, data.size, Inet4Address.getByName("224.0.0.63"), 1944))

        // Recommended re-advertising interval (5 seconds)
        return 5000
    }

    override fun addApplication(namespace: ByteArray) {
        applications.add(namespace.toHashableSequence())
    }

    override fun removeApplication(namespace: ByteArray) {
        applications.remove(namespace.toHashableSequence())
    }

    override fun getAddresses(): List<BinaryAddress> {
        return listOf(ipv4!!.getAddress())
    }

    override fun start() {
        // Join multicast group
        val group = Inet4Address.getByName("224.0.0.63")
        socket.joinGroup(group)

        up = true

        thread {
            while (up) {
                // Ready the buffer
                val buffer = ByteArray(65536)

                // Create the packet
                val packet = DatagramPacket(buffer, buffer.size)

                // Receive the next packet
                socket!!.receive(packet)

                // Cut to length
                val packetData = packet.data.sliceArray(IntRange(0, packet.length - 1))

                try {
                    // Deserialise the packet
                    val sambandPacket = SambandPacket.deserialise(packetData)

                    // Get unique packet ID
                    val id = sambandPacket.id.toHashableSequence()

                    // Only process unique IDs
                    if (!receivedIds.contains(id)) {
                        receivedIds.add(id)

                        // Is it an application we want to know about?
                        if (applications.contains(sambandPacket.addreess.application.toHashableSequence())) {
                            // Notify subscribers
                            discovered.onNext(sambandPacket.toDiscovery())
                        }

                    }
                }
                catch (e: IOException) {
                    // No need to do anything
                }

            }
        }
    }

    override fun stop() {
        up = false
    }

    init {
        // Find an IPv4 network (required for this discoverer)
        ipv4 = networks.first { it is Ipv4 } as Ipv4

        // Error if no valid network
        if(ipv4 == null) {
            throw IOException("Samband discoverer cannot work without an IPv4 network")
        }
    }

}