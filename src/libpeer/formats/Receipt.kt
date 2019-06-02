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

}

enum class Layer {
    APPLICATION,
    INTERFACE,
    MODIFIER,
    TRANSPORT,
    MUXER,
    NETWORK
}