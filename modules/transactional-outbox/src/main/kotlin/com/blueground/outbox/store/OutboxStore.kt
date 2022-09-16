package com.blueground.outbox.store

import com.blueground.outbox.item.OutboxItem

interface OutboxStore {
  fun insert(outboxItem: OutboxItem): OutboxItem

  fun update(outboxItem: OutboxItem): OutboxItem?

  fun fetch(outboxFilter: OutboxFilter): List<OutboxItem>
}
