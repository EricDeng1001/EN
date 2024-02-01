package model.executor.task

enum class Status {
    inited, pending, running, success, failed,
}

abstract class Stateful {
    abstract fun status(): Status

    companion object {
        fun mergeStatus(statuses: Iterable<Stateful>): Status {
            var s = Status.pending
            var successCount = 0
            for (t in statuses) {
                val tStatus = t.status()
                if (tStatus == Status.failed) {
                    return Status.failed
                }
                if (tStatus in listOf(Status.pending, Status.running)) {
                    s = Status.running
                }
                if (tStatus == Status.success) {
                    successCount++
                }
            }
            if (successCount == statuses.count()) {
                s = Status.success
            }
            return s
        }
    }

    fun done(): Boolean {
        return this.status() in listOf(Status.success, Status.failed)
    }

    fun inited(): Boolean {
        return this.status() == Status.inited
    }

    fun running(): Boolean {
        return this.status() in listOf(Status.pending, Status.running)
    }

    fun success(): Boolean {
        return this.status() == Status.success
    }

    fun failed(): Boolean {
        return this.status() == Status.failed
    }
}



