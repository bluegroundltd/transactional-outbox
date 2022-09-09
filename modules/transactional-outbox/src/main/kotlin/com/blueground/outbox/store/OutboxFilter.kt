package com.blueground.outbox.store

import com.blueground.outbox.item.OutboxStatus
import java.time.Instant

sealed class AbstractOutboxFilter(
  protected val status: OutboxStatus
)

class OutboxPendingFilter(
  val nextRunLessThan: Instant
) : AbstractOutboxFilter(OutboxStatus.PENDING)

class OutboxRunningFilter(
  val rerunAfterGreaterThan: Instant
) : AbstractOutboxFilter(OutboxStatus.RUNNING)

class OutboxFilter(
  nextExecutionAtGreaterThan: Instant,
  rerunAfterGreaterThan: Instant = nextExecutionAtGreaterThan
) {
  val outboxPendingFilter: OutboxPendingFilter = OutboxPendingFilter(nextExecutionAtGreaterThan)
  val outboxRunningFilter: OutboxRunningFilter = OutboxRunningFilter(rerunAfterGreaterThan)
}
