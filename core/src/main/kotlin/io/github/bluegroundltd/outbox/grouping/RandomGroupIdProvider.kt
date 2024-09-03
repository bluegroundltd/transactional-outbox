package io.github.bluegroundltd.outbox.grouping

import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType
import java.util.UUID

/**
 * An [OutboxGroupIdProvider] that generates random group IDs.
 */
internal class RandomGroupIdProvider : OutboxGroupIdProvider {
  override fun execute(type: OutboxType, payload: OutboxPayload): String = UUID.randomUUID().toString()
}
