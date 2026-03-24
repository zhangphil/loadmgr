package lib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import java.util.concurrent.PriorityBlockingQueue

class LoadMgr {
    companion object {
        private const val TAG = "fly/LoadMgr"

        val INSTANCE = LoadMgr()
        val THREAD_POOL = newFixedThreadPoolContext(nThreads = 4, name = "线程")
    }

    private val mChannel = Channel<LoadRequest>()

    private val bufferCapacity = 10
    private val initialCapacity = 50

    private val mPriorityBlockingQueue = PriorityBlockingQueue(
        initialCapacity,
        Comparator<LoadRequest> { o1, o2 -> o2.getPriority()!!.ordinal - o1.getPriority()!!.ordinal })

    private constructor() {
        println("$TAG constructor")
    }

    fun startup() {
        //接收任务
        CoroutineScope(THREAD_POOL).launch {
            println("$TAG Channel start... ${Thread.currentThread().name}")

            mChannel.receiveAsFlow()
                .onEach { it ->  //生产者
                    //println("$TAG onEach-$it ${Thread.currentThread().name}")
                }.buffer(bufferCapacity)
                .collect { it -> //消费者
                    //collect, 这里相当于通过缓冲后匀速发射过来的触发器(trigger)。
                    //收集到的值在此并不重要，这里，只是把它作为触发信号。
                    //println("$TAG collect-$it ${Thread.currentThread().name}")
                    trigger()
                }
        }
    }

    private fun trigger() {
        val loadRequest = mPriorityBlockingQueue.poll()
        println("$TAG 当前最大优先级任务:${loadRequest} ${Thread.currentThread().name}")

        loadRequest?.let {
            CoroutineScope(THREAD_POOL).launch {
                val result = if (it.isCancelled()) {
                    println("$TAG id=${loadRequest.getId()} isCancelled=${it.isCancelled()}")
                    return@launch
                } else {
                    it.getListener()?.onStart(it)
                    it.getLoader()?.doInBackground()
                }

                println("$TAG id=${loadRequest} doInBackground完成 isCancelled=${loadRequest.isCancelled()} ${Thread.currentThread().name}")
                if (it.isCancelled()) {
                    // do noting
                } else {
                    it.getListener()?.onSuccess(it, result)
                    println("$TAG deliveryResult loadRequest=${loadRequest} ${Thread.currentThread().name}")
                    it.getLoader()?.deliveryResult(result)
                }
            }
        }
    }

    fun enqueue(taskInfo: LoadRequest) {
        CoroutineScope(THREAD_POOL).launch {
            mPriorityBlockingQueue.add(taskInfo)
            mChannel.send(taskInfo)
        }
    }

    fun submit(priority: Priority = Priority.NORMAL, loader: Loader): LoadRequest {
        val request = LoadRequest.Builder()
            .priority(priority)
            .loader(loader)
            .build()

        enqueue(request)

        return request
    }

    fun submit(priority: Priority = Priority.NORMAL, func: () -> Unit): LoadRequest {
        val loader = object : SimpleLoader() {
            override fun worker() {
                func.invoke()
            }
        }

        val request = LoadRequest.Builder()
            .priority(priority)
            .loader(loader)
            .build()

        enqueue(request)

        return request
    }

    fun destroy() {
        mPriorityBlockingQueue.clear()
        mChannel.cancel()
        mChannel.close()
    }
}
