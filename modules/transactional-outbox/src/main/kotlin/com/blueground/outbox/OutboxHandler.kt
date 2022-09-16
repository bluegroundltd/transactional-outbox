package com.blueground.outbox

import com.blueground.outbox.item.OutboxPayload
import com.blueground.outbox.item.OutboxType
import java.time.Instant

interface OutboxHandler {
  fun supports(type: OutboxType): Boolean

  fun serialize(payload: OutboxPayload): String

  fun getNextExecutionTime(currentRetries: Long): Instant

  fun hasReachedMaxRetries(retries: Long): Boolean

  fun handle(payload: String)

  fun handleFailure(payload: String)
}
