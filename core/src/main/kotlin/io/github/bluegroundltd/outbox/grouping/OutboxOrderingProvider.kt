package io.github.bluegroundltd.outbox.grouping

import io.github.bluegroundltd.outbox.item.OutboxItem

/**
 * Defines a provider that can be used to order outbox items.
 */
interface OutboxOrderingProvider {
  fun execute(items: Iterable<OutboxItem>): List<OutboxItem>
}
