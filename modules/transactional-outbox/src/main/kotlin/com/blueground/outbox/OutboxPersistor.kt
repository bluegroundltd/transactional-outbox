package com.blueground.outbox

import com.blueground.outbox.item.OutboxItem

interface OutboxPersistor {
  fun insert(outboxItem: OutboxItem)
}
