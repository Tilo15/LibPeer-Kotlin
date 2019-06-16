package libpeer.util

import java.util.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.UUID




fun UUID.toByteArray(): ByteArray {
    val ba = ByteArrayOutputStream(16)
    val da = DataOutputStream(ba)
    da.writeLong(this.mostSignificantBits)
    da.writeLong(this.leastSignificantBits)
    return ba.toByteArray()
}

fun UUID.toHashableSequence(): HashableSequence {
    return this.toByteArray().toHashableSequence()
}

fun uuidOf(bytes: ByteArray): UUID {
    val bb = ByteBuffer.wrap(bytes)
    val high = bb.long
    val low = bb.long
    return UUID(high, low)
}

