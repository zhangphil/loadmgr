package lib

import java.util.UUID

open class Loader {
    companion object {
        private const val TAG = "fly/Loader"
    }

    private var id: Any? = UUID.randomUUID()

    constructor() {

    }

    open fun setId(id: Any?) {
        this.id = id
    }

    open fun getId(): Any? {
        return id
    }

    open fun doInBackground(): Result<Any>? {
        return null
    }

    open fun deliveryResult(result: Result<Any>?) {

    }

    override fun equals(other: Any?): Boolean {
        return id == (other as Loader).id
    }
}