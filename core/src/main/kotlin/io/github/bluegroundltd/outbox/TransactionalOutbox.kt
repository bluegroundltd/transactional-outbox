package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType

// TODO document both interface and each method (expected functionality)
interface TransactionalOutbox {

  fun add(type: OutboxType, payload: OutboxPayload)

  fun monitor()
}
