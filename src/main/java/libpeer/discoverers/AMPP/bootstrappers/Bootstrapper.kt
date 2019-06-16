package libpeer.discoverers.AMPP.bootstrappers

import io.reactivex.subjects.Subject
import libpeer.formats.BinaryAddress

interface Bootstrapper {
    val discovered: Subject<BinaryAddress>

    fun start()

    fun stop()

}