package com.blueground.outbox.store

import com.blueground.outbox.item.OutboxItem

interface OutboxStore {
  fun insert(outboxItem: OutboxItem)

  fun update(outboxItem: OutboxItem)

  fun fetch(outboxFilter: OutboxFilter): List<OutboxItem>
}
