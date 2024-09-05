package io.github.bluegroundltd.outbox.grouping

import io.github.bluegroundltd.outbox.item.OutboxItem

/**
 * An [OutboxOrderingProvider] that does not apply any ordering on the supplied [OutboxItem]s, essentially
 * returning them in the same order they were provided.
 */
class FifoOrderingProvider : OutboxOrderingProvider {
  override fun execute(items: Iterable<OutboxItem>): List<OutboxItem> = items.map { it }
}
