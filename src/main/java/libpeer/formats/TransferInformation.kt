package libpeer.formats

class TransferInformation(val size: Long, val received: Long, val read: Long) {

    var receivedFraction: Float = 0f
    var readFraction: Float = 0f

    init {
        receivedFraction = received.toFloat() / size
        readFraction = read.toFloat() / size
    }

}