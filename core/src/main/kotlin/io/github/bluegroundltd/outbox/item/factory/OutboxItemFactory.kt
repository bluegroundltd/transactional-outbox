package io.github.bluegroundltd.outbox.item.factory

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.annotation.TestableOpenClass
import io.github.bluegroundltd.outbox.grouping.OutboxGroupIdProvider
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType

@TestableOpenClass
internal class OutboxItemFactory(
  private val outboxHandlers: Map<OutboxType, OutboxHandler>,
  private val groupIdProvider: OutboxGroupIdProvider
) {

  fun makeScheduledOutboxItem(type: OutboxType, payload: OutboxPayload): OutboxItem {
    val handler = findHandler(type)
    return OutboxItem(
      type = type,
      status = OutboxStatus.PENDING,
      payload = handler.serialize(payload),
      // ensures that instant outbox items are picked up by monitor's fetching and are eligible for processing
      nextRun = handler.getNextExecutionTime(0).minusMillis(1),
      groupId = groupIdProvider.execute(type, payload)
    )
  }

  private fun findHandler(type: OutboxType) = outboxHandlers[type]
    ?: throw UnsupportedOperationException("Outbox item type \"{${type.getType()}\" isn't supported")
}
