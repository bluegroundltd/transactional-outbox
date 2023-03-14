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
  rerunAfterLessThan: Instant = nextRunLessThan
) {
  val outboxPendingFilter: OutboxPendingFilter = OutboxPendingFilter(nextRunLessThan)
  val outboxRunningFilter: OutboxRunningFilter = OutboxRunningFilter(rerunAfterLessThan)
}
