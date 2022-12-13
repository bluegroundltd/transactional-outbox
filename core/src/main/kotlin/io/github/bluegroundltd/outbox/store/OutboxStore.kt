package io.github.bluegroundltd.outbox.store

import io.github.bluegroundltd.outbox.item.OutboxItem

interface OutboxStore {
  fun insert(outboxItem: OutboxItem): OutboxItem

  fun update(outboxItem: OutboxItem): OutboxItem?

  fun fetch(outboxFilter: OutboxFilter): List<OutboxItem>
}
