package io.github.bluegroundltd.outbox.grouping

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxItemGroup
import java.util.UUID

/**
 * An [OutboxGroupingProvider] that groups [OutboxItem]s by their group ID.
 *
 * If an item does not have a group ID set, a temporary random id (UUID) is used to essentially create a
 * 'group of one'. This is done (instead e.g. of pre-filtering the items without group id) to ensure that
 * the **order of groups** remain consistent with the original item order. The order of items **within a group**
 * is determined by the supplied [OutboxOrderingProvider] which defaults to [FifoOrderingProvider].
 */
internal class GroupIdGroupingProvider(
  private val orderingProvider: OutboxOrderingProvider = FifoOrderingProvider()
) : OutboxGroupingProvider {
  override fun execute(items: Iterable<OutboxItem>) = items
    .groupBy { it.groupId ?: UUID.randomUUID() } // group by group ID
    .map { orderingProvider.execute(it.value) } // order items in each group
    .map { OutboxItemGroup(it) } // create groups
}
