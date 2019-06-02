package libpeer.interfaces

import libpeer.formats.Reception

interface Interface {
    fun processReception(reception: Reception)
}