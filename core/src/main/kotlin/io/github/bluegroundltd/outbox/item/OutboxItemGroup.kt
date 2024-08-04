package io.github.bluegroundltd.outbox.item

data class OutboxItemGroup(
  val items: List<OutboxItem>
) : Iterable<OutboxItem> by items
