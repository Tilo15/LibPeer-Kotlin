package libpeer.discoverers.AMPP.bootstrappers

import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import libpeer.discoverers.AMPP.AMPP
import libpeer.formats.BinaryAddress
import libpeer.networks.Ipv4
import org.xbill.DNS.ARecord
import org.xbill.DNS.Lookup
import org.xbill.DNS.TXTRecord
import org.xbill.DNS.Type
import java.lang.Exception
import kotlin.text.Charsets.UTF_8

class DNS : Bootstrapper {

    companion object {
        val DOMAINS = listOf("libpeer.localresolver", "libpeer.pcthingz.com", "libpeer.unitatem.net", "libpeer.mooo.com")
    }

    override fun start() {
        for(entry in DOMAINS) {
            // Lookup the domain name
            try {
                val records = Lookup(entry, Type.TXT).run()
                for(record in records) {
                    val txt = record as TXTRecord
                    val values = txt.strings.map { it.toString() }
                    for(value in values) {
                        // Is it a valid entry?
                        if(value.startsWith("LP:")) {
                            // Get the entry type
                            val entryType = value.substring(3, 7)

                            if(entryType == "MOTD") {
                                // Message of the day
                                println("[DNS Bootstrapper] $entry says: ${value.substring(8).replace("::", ":")}")
                            }

                            if(entryType == "ADDR") {
                                // IPv4 address
                                val info = value.substring(8).split(":")

                                // Add peer ( TODO XXX don't intialise an IPv4 instance )
                                discovered.onNext(BinaryAddress(Ipv4.IDENTIFIER, info[0].toByteArray(UTF_8), info[1].toByteArray(), AMPP.NAMESPACE.byteArray))
                            }

                            if(entryType == "NAME") {
                                // DNS name
                                val info = value.substring(8).split(":")

                                // Query for address(es)
                                try {
                                    val ipRecords = Lookup(info[0], Type.A).run()
                                    for (ipRecord in ipRecords) {
                                        val a = ipRecord as ARecord

                                        // Add peer
                                        discovered.onNext(BinaryAddress(Ipv4.IDENTIFIER, a.address.hostAddress.toByteArray(), info[1].toByteArray(), AMPP.NAMESPACE.byteArray))
                                    }
                                }
                                catch (e: Exception) {
                                    // TODO log maybe?
                                }
                            }
                        }
                    }
                }
            }
            catch (e: Exception) {
                // TODO log maybe?
            }
        }
    }

    override fun stop() {

    }

    override val discovered: Subject<BinaryAddress> = PublishSubject.create()

}