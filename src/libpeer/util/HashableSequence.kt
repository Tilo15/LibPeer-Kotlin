package libpeer.util

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8

class HashableSequence(val byteArray: ByteArray) {

    override fun hashCode(): Int {
        return byteArray.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if(other is HashableSequence) {
            return byteArray.contentEquals(other.byteArray)
        }
        if(other is ByteArray) {
            return byteArray.contentEquals(other)
        }
        return false
    }

    override fun toString(): String {
        return byteArray.toString(UTF_8)
    }

    fun toString(charset: Charset): String {
        return byteArray.toString(charset)
    }

}

fun ByteArray.toHashableSequence(): HashableSequence {
    return HashableSequence(this)
}