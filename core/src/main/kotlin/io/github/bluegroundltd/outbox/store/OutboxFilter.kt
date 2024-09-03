package io.github.bluegroundltd.outbox.store

import io.github.bluegroundltd.outbox.item.OutboxStatus
import java.time.Instant

sealed class AbstractOutboxFilter(
  val status: OutboxStatus
)

class OutboxPendingFilter(
  val nextRunLessThan: Instant
) : AbstractOutboxFilter(OutboxStatus.PENDING)

class OutboxRunningFilter(
  val rerunAfterLessThan: Instant
) : AbstractOutboxFilter(OutboxStatus.RUNNING)

class OutboxFilter(
  nextRunLessThan: Instant,
  rerunAfterLessThan: Instant = nextRunLessThan,
  val id: Long? = null // if set, it should fetch the item with this id instead of batch of items
) {
  val outboxPendingFilter: OutboxPendingFilter = OutboxPendingFilter(nextRunLessThan)
  val outboxRunningFilter: OutboxRunningFilter = OutboxRunningFilter(rerunAfterLessThan)
}
