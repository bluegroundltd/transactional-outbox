package io.github.bluegroundltd.outbox.item

import java.time.Instant

/**
 * Represents an outbox item.
 *
 * @param groupId an arbitrary value that can be used to group outbox items together. A null value indicates that the
 * item is not part of a group.
 *
 * @property markedForProcessing is an internal flag that is used to determine if the item should be processed in the
 * current monitor cycle. It is set by [prepareForProcessing] based on the item status and current time.
 * Clients could (and should) ignore the value. Even if set earlier, its value be reset during the monitor cycle.
 */
data class OutboxItem(
  val id: Long? = null,
  val type: OutboxType,
  var status: OutboxStatus,
  val payload: String,
  var retries: Long = 0,
  var nextRun: Instant,
  var lastExecution: Instant? = null,
  var rerunAfter: Instant? = null,
  var deleteAfter: Instant? = null,
  val groupId: String? = null
) {

  var markedForProcessing: Boolean = false
    private set

  /**
   * Resolves whether the item is eligible for processing based on its state (i.e. status, timestamps, etc.) and
   * the current time (supplied as a parameter). If the item is eligible for processing, it is marked as such
   * and its status and run timestamps are updated accordingly.
   *
   * @param now the current time
   * @param rerunAfter the timestamp to which the `rerunAfter` property should be set if the item is indeed
   * eligible for processing and is marked as such.
   */
  fun prepareForProcessing(now: Instant, rerunAfter: Instant) {
    markedForProcessing = markedForProcessing || isEligibleForProcessing(now)
    if (markedForProcessing) {
      status = OutboxStatus.RUNNING
      lastExecution = now
      this.rerunAfter = rerunAfter
    }
  }

  private fun isEligibleForProcessing(now: Instant): Boolean =
    isPendingEligibleForProcessing(now) || isRunningEligibleForProcessing(now)

  private fun isPendingEligibleForProcessing(now: Instant): Boolean =
    status == OutboxStatus.PENDING && nextRun.isBefore(now)

  private fun isRunningEligibleForProcessing(now: Instant): Boolean =
    status == OutboxStatus.RUNNING && rerunAfter != null && rerunAfter!!.isBefore(now)
}
