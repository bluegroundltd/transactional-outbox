package io.github.bluegroundltd.outbox.grouping

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxItemGroup

/**
 * An [OutboxGroupingProvider] that groups [OutboxItem]s by their group ID.
 *
 * The provider assumes that all items have a group ID set. Otherwise, an exception will be thrown.
 */
internal class GroupIdGroupingProvider(
  private val orderingProvider: OutboxOrderingProvider = FifoOrderingProvider()
) : OutboxGroupingProvider {
  override fun execute(items: Iterable<OutboxItem>) = items
    .groupBy { it.groupId!! } // group by group ID
    .map { orderingProvider.execute(it.value) } // order items in each group
    .map { OutboxItemGroup(it) } // create groups
}
