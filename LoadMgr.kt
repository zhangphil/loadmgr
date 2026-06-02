package lib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.PriorityBlockingQueue


class LoadMgr {
    companion object {
        private const val TAG = "fly/LoadMgr"

        const val DEFAULT_THREAD_SIZE = 4
        val INSTANCE = LoadMgr()
    }

    private val mThreadPoolName = "LoadMgr"

    private lateinit var mExecutorCoroutine: ExecutorCoroutineDispatcher
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
        startup(DEFAULT_THREAD_SIZE)
    }

    fun startup(threads: Int) {
        mExecutorCoroutine = newFixedThreadPoolContext(nThreads = threads, name = mThreadPoolName)

        //接收任务
        CoroutineScope(mExecutorCoroutine).launch {
            println("$TAG start... threads=$threads ${Thread.currentThread().name}")

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

        loadRequest?.let { it_loadRequest ->
            CoroutineScope(mExecutorCoroutine).launch {
                val result = if (it_loadRequest.isCancelled()) {
                    println("$TAG id=${loadRequest.getId()} isCancelled=${it_loadRequest.isCancelled()}")
                    return@launch
                } else {
                    it_loadRequest.getListener()?.onStart(it_loadRequest)
                    val timeout = loadRequest.getTimeout()
                    if (timeout > 0) {
                        runCatching {
                            withTimeoutOrNull(timeout) {
                                it_loadRequest.getLoader()?.doInBackground()
                            }
                        }.onFailure { it_throwable ->
                            print("$it_throwable $loadRequest")
                        }.getOrNull()
                    } else {
                        it_loadRequest.getLoader()?.doInBackground()
                    }
                }

                println("$TAG id=${loadRequest} doInBackground完成 isCancelled=${loadRequest.isCancelled()} ${Thread.currentThread().name}")
                if (it_loadRequest.isCancelled()) {
                    // do noting
                } else {
                    it_loadRequest.getListener()?.onSuccess(it_loadRequest, result)
                    println("$TAG deliveryResult loadRequest=${loadRequest} ${Thread.currentThread().name}")
                    it_loadRequest.getLoader()?.deliveryResult(result)
                }
            }
        }
    }

    fun enqueue(taskInfo: LoadRequest) {
        CoroutineScope(mExecutorCoroutine).launch {
            mPriorityBlockingQueue.add(taskInfo)
            mChannel.send(taskInfo)
        }
    }

    fun submit(
        priority: Priority = Priority.NORMAL,
        loader: Loader,
        listener: LoadRequest.Listener? = null
    ): LoadRequest? {
        val request = LoadRequest.Builder()
            .priority(priority)
            .loader(loader)
            .listener(listener)
            .build()

        enqueue(request)

        return request
    }

    fun submit(priority: Priority = Priority.NORMAL, loader: Loader): LoadRequest? {
        return submit(priority, loader, null)
    }

    fun submit(
        priority: Priority = Priority.NORMAL,
        func: () -> Unit,
        listener: LoadRequest.Listener? = null
    ): LoadRequest? {
        val loader = object : SimpleLoader() {
            override fun worker() {
                func.invoke()
            }
        }

        return submit(priority, loader, listener)
    }

    fun submit(priority: Priority = Priority.NORMAL, timeout: Long, func: () -> Unit): LoadRequest? {
        val loader = object : SimpleLoader() {
            override fun worker() {
                func.invoke()
            }
        }

        val request = LoadRequest.Builder()
            .priority(priority)
            .loader(loader)
            .timeout(timeout)
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