package io.github.bluegroundltd.outbox.grouping

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxItemGroup

/**
 * An [OutboxGroupingProvider] that groups [OutboxItem]s by their group ID.
 *
 * The provider assumes that all items have a group ID set. Otherwise, an exception will be thrown.
 */
internal class GroupIdGroupingProvider : OutboxGroupingProvider {
  override fun execute(items: Iterable<OutboxItem>) = items.groupBy { it.groupId!! }.map { OutboxItemGroup(it.value) }
}
