package libpeer.formats

class Reception(
    val data: ByteArray,
    val transport: Byte,
    val channel: ByteArray,
    val peer: BinaryAddress
)