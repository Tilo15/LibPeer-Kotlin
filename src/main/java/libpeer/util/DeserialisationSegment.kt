package libpeer.util


class DeserialisationSegment {

    val segment: ByteArray
    val remainder: ByteArray

    constructor(data: ByteArray, delimiter: Byte, start: Int = 0) {
        // Find the delimiter
        // TODO make this actually tolerable
        val position = data.sliceArray(IntRange(start, data.size - 1)).indexOf(delimiter) + start

        // Create the ByteArray to hold the segment
        segment = ByteArray(position - start)

        // Copy the data
        data.copyInto(segment, 0, start, position)

        // Create the ByteArray to hold the remaining data
        remainder = ByteArray(data.size - position - 1)

        // Copy the data
        data.copyInto(remainder, 0, position + 1)
    }

    fun nextSegement(delimiter: Byte, start: Int = 0): DeserialisationSegment {
        // Return a segment based on this segment
        return DeserialisationSegment(remainder, delimiter, start)
    }
}