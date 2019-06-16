package libpeer.discoverers.AMPP.bootstrappers

import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import libpeer.discoverers.AMPP.AMPP
import libpeer.discoverers.Samband
import libpeer.formats.BinaryAddress
import libpeer.networks.Network
import kotlin.concurrent.thread

class Ipv4Multicast(networks: List<Network>): Bootstrapper {
    override val discovered: Subject<BinaryAddress> = PublishSubject.create()

    private val samband: Samband = Samband(networks)
    private var running: Boolean = false

    init {
        samband.addApplication(AMPP.NAMESPACE.byteArray)
        samband.discovered.subscribe { discovered.onNext(it.address) }
    }

    override fun start() {
        samband.start()

        thread {
            while(running) {
                val addresses = samband.getAddresses()
                var delay = 0L

                for (address in addresses) {
                    val advertiseAddress = BinaryAddress(address.networkType, address.networkAddress, address.networkPort, AMPP.NAMESPACE.byteArray)
                    delay = samband.advertise(advertiseAddress).toLong()
                }

                Thread.sleep(delay)
            }
        }
    }

    override fun stop() {
        running = false
        samband.stop()
    }


}