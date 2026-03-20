package lib

import java.util.UUID

open class LoadRequest {
    private var id: Any? = null
    private var tag: Any? = null
    private var loader: Loader? = null
    private var priority: Priority? = null
    private var listener: Listener? = null
    private var isCancelled = false

    private constructor() {

    }

    fun getId(): Any? {
        return id
    }

    fun getTag(): Any? {
        return tag
    }

    fun getLoader(): Loader? {
        return loader
    }

    fun getListener(): Listener? {
        return listener
    }

    fun getPriority(): Priority? {
        return priority
    }

    open fun cancel() {
        isCancelled = true

        listener?.onCancel(this)
    }

    open fun isCancelled(): Boolean {
        return isCancelled
    }

    override fun equals(other: Any?): Boolean {
        return (id == (other as LoadRequest).id) && (tag == (other.tag))
    }

    override fun toString(): String {
        return "LoadRequest(id=$id, tag=$tag, priority=$priority, isCancelled=$isCancelled)"
    }

    interface Listener {
        fun onStart(request: LoadRequest?) {}
        fun onCancel(request: LoadRequest?) {}
        fun onError() {}
        fun onSuccess(request: LoadRequest?, result: Any?) {}
    }

    class Builder {
        private var id: Any? = UUID.randomUUID()
        private var tag: Any? = null
        private var loader: Loader? = null
        private var priority: Priority? = Priority.NORMAL
        private var listener: Listener? = null

        constructor() {

        }

        fun id(id: Any): Builder {
            this.id = id
            return this
        }

        fun tag(tag: Any): Builder {
            this.tag = tag
            return this
        }

        fun loader(loader: Loader): Builder {
            this.loader = loader
            return this
        }

        fun listener(listener: Listener): Builder {
            this.listener = listener
            return this
        }

        fun priority(priority: Priority): Builder {
            this.priority = priority
            return this
        }

        fun build(): LoadRequest {
            val request = LoadRequest()
            request.id = id
            request.tag = tag
            request.loader = loader
            request.priority = priority
            request.listener = listener
            return request
        }
    }
}