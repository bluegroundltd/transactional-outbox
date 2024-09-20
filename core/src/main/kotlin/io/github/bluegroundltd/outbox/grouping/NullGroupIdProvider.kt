package io.github.bluegroundltd.outbox.grouping

import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType

/**
 * An [OutboxGroupIdProvider] that always returns null, indicating that the item is not part of a group.
 */
internal class NullGroupIdProvider : OutboxGroupIdProvider {
  override fun execute(type: OutboxType, payload: OutboxPayload): String? = null
}
