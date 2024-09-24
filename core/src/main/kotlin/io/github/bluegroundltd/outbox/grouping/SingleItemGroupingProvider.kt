package io.github.bluegroundltd.outbox.grouping

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxItemGroup

/**
 * An [OutboxGroupingProvider] that generates groups of single [OutboxItem]s (i.e. one group for each item).
 */
internal class SingleItemGroupingProvider : OutboxGroupingProvider {
  override fun execute(items: Iterable<OutboxItem>) = items.map { OutboxItemGroup.of(it) }
}
