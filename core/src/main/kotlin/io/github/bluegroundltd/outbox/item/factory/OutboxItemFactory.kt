package io.github.bluegroundltd.outbox.item.factory

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.annotation.TestableOpenClass
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
import java.time.Clock
import java.time.Duration
import java.time.Instant

@TestableOpenClass
internal class OutboxItemFactory(
  private val clock: Clock,
  private val outboxHandlers: Map<OutboxType, OutboxHandler>,
  private val rerunAfterDuration: Duration,
) {

  fun makeScheduledOutboxItem(type: OutboxType, payload: OutboxPayload): OutboxItem {
    val handler = findHandler(type)
    return OutboxItem(
      type = type,
      status = OutboxStatus.PENDING,
      payload = handler.serialize(payload),
      nextRun = handler.getNextExecutionTime(0),
    )
  }

  fun makeOnDemandOutboxItem(type: OutboxType, payload: OutboxPayload): OutboxItem {
    val handler = findHandler(type)
    val now = Instant.now(clock)
    return OutboxItem(
      type = type,
      status = OutboxStatus.RUNNING,
      payload = handler.serialize(payload),
      nextRun = handler.getNextExecutionTime(0),
      lastExecution = now,
      rerunAfter = now.plus(rerunAfterDuration)
    )
  }

  private fun findHandler(type: OutboxType) = outboxHandlers[type]
    ?: throw UnsupportedOperationException("Outbox item type \"{${type.getType()}\" isn't supported")
}
