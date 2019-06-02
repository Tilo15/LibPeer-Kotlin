package libpeer.formats

import libpeer.util.toByteArray
import libpeer.util.toHashableSequence
import libpeer.util.uuidOf
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.*
import java.util.zip.Adler32

class Chunk(
    val id: UUID,
    val previousChunkId: UUID,
    val payload: ByteArray,
    var timeSent: Double,
    private var lightChecksum: UInt,
    private val fullChecksum: ByteArray,
    val isValid: Boolean = true
) {

    fun serialise(): ByteArray {
        // Create the data section of the chunk
        val dataSection = id.toByteArray() + previousChunkId.toByteArray() + payload

        val lightHasher = Adler32()
        lightHasher.update(payload)
        lightChecksum = lightHasher.value.toUInt()

        // We are probably sending very shortly
        timeSent = System.currentTimeMillis() / 1000.0


        val numericSection = ByteBuffer.allocate(12)
            .putDouble(timeSent)
            .putInt(lightChecksum.toInt())
            .array()

        return numericSection + fullChecksum + dataSection
    }

    companion object {

        fun create(payload: ByteArray, previousChunkId: UUID = uuidOf(ByteArray(16)), fullHash: Boolean = false): Chunk{
            return Chunk(UUID.randomUUID(), previousChunkId, payload, 0.0, 0u, ByteArray(16))
        }

        fun deserialise(data: ByteArray): Chunk {

            val numericSectionBuffer = ByteBuffer.wrap(data, 0, 12)

            // Intentionally left using old style of accessors for readability
            val timeSent = numericSectionBuffer.getDouble()
            val lightChecksum = numericSectionBuffer.getInt().toUInt()

            // Get full checksum
            val fullChecksum = ByteArray(16)
            data.copyInto(fullChecksum, 0, 12, 28)

            // Get Data Section
            val dataSection = ByteArray(data.size - 28)
            data.copyInto(dataSection, 0, 28)

            // Get chunk ID
            val id = ByteArray(16)
            dataSection.copyInto(id, 0, 0, 16)

            // Get previous chunk ID
            val previousId = ByteArray(16)
            dataSection.copyInto(previousId, 0, 16, 32)

            // Get payload
            val payload = ByteArray(dataSection.size - 32)
            dataSection.copyInto(payload, 0, 32)

            // Calculate checksums
            val lightHasher = Adler32()
            lightHasher.update(payload)

            var validHashes = false

            if(lightHasher.value.toUInt() == lightChecksum) {

                // Don't calculate if full checksum was not sent
                if(fullChecksum.contentEquals(ByteArray(16))) {
                    validHashes = true
                }
                else {
                    // Calculate full checksum
                    val fullHash = MessageDigest.getInstance("MD5").digest(dataSection)
                    if(fullHash.contentEquals(fullChecksum)) {
                        validHashes = true
                    }
                }

            }

            // Return new chunk
            return Chunk(uuidOf(id), uuidOf(previousId), payload, timeSent, lightChecksum, fullChecksum, validHashes)

        }

    }


}