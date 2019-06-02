package libpeer.application.standalone

import java.util.concurrent.CountDownLatch

class Action<T>(private val call: (Action<T>) -> Any) {

    private val latch: CountDownLatch = CountDownLatch(1)
    private var response: T? = null

    fun wait(): T {
        latch.await()
        return response!!
    }

    fun run() {
        call(this)
    }

    fun complete(result: T) {
        response = result
        latch.countDown()
    }

}