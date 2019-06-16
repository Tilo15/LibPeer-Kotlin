package libpeer.formats

class Receipt(val success: Boolean, val errorLayer: Layer? = null, val errorMessage: String? = null) {

    companion object {
        fun success(): Receipt {
            return Receipt(true)
        }

        fun error(layer: Layer, message: String): Receipt {
            return Receipt(false, layer, message)
        }
    }

    fun toErrorString(): String {
        return "An error occurred at the ${errorLayer!!.toString().toLowerCase()} layer of LibPeer: ${errorMessage!!}"
    }

}

enum class Layer {
    APPLICATION,
    INTERFACE,
    MODIFIER,
    TRANSPORT,
    MUXER,
    NETWORK
}