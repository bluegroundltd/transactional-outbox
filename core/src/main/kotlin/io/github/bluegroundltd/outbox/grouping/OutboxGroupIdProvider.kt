package io.github.bluegroundltd.outbox.grouping

import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType

/**
 * Defines a provider that can be used to generate group IDs for outbox items.
 */
interface OutboxGroupIdProvider {
  fun execute(type: OutboxType, payload: OutboxPayload): String?
}
