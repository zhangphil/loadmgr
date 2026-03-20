package lib

open class SimpleLoader : Loader() {
    open fun worker() {

    }

    final override fun doInBackground(): Result<Any>? {
        worker()
        return null
    }
}