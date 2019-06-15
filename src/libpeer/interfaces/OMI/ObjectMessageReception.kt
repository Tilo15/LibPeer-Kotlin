package libpeer.interfaces.OMI

import libpeer.formats.BinaryAddress

class ObjectMessageReception(val objectMessage: ObjectMessage, val peer: BinaryAddress) {
}