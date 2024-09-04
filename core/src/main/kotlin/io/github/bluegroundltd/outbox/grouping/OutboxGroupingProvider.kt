package io.github.bluegroundltd.outbox.grouping

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxItemGroup

/**
 * Defines a provider that can be used to group outbox items.
 */
interface OutboxGroupingProvider {
  fun execute(items: Iterable<OutboxItem>): List<OutboxItemGroup>
}
