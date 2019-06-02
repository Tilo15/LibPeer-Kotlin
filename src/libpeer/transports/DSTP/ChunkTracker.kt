package libpeer.transports.DSTP

import io.reactivex.subjects.PublishSubject
import libpeer.formats.Chunk
import libpeer.formats.Layer
import libpeer.formats.Receipt
import libpeer.util.HashableSequence
import libpeer.util.toHashableSequence

class ChunkTracker(private val chunks: HashMap<HashableSequence, Chunk>) {
    val sent: PublishSubject<Receipt> = PublishSubject.create()

    var complete: Boolean = false
    private var chunkCount: Int = 0

    fun chunkAcknowledged(chunkId: HashableSequence) {
        // Is it one of ours?
        if(chunks.contains(chunkId)) {
            // Remove from the set
            chunks.remove(chunkId)

            // Are all our chunks gone?
            if(chunks.size == 0) {
                complete = true
                sent.onNext(Receipt.success())
                sent.onComplete()
            }
        }
    }

    fun canceled(reason: String) {
        // Only cause an error if it was incomplete
        if(!complete) {
            sent.onNext(Receipt.error(Layer.TRANSPORT, reason))
            sent.onComplete()
        }
    }

    companion object {
        fun create(chunks: List<Chunk>): ChunkTracker {

            val map = HashMap<HashableSequence, Chunk>()
            chunks.forEach {
                map[it.id.toHashableSequence()] = it
            }

            return ChunkTracker(map)

        }
    }

}