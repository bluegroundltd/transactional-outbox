package com.blueground.outbox

import com.blueground.outbox.item.OutboxPayload
import com.blueground.outbox.item.OutboxType

// TODO document both interface and each method (expected functionality)
interface TransactionalOutbox {

  fun add(type: OutboxType, payload: OutboxPayload)

  fun monitor()
}
