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
  val rerunAfterGreaterThan: Instant
) : AbstractOutboxFilter(OutboxStatus.RUNNING)

class OutboxFilter(
  nextExecutionAtGreaterThan: Instant,
  rerunAfterGreaterThan: Instant = nextExecutionAtGreaterThan
) {
  val outboxPendingFilter: OutboxPendingFilter = OutboxPendingFilter(nextExecutionAtGreaterThan)
  val outboxRunningFilter: OutboxRunningFilter = OutboxRunningFilter(rerunAfterGreaterThan)
}
