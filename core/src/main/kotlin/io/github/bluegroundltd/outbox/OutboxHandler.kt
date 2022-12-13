package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType
import java.time.Instant

interface OutboxHandler {
  fun getSupportedType(): OutboxType

  fun serialize(payload: OutboxPayload): String

  fun getNextExecutionTime(currentRetries: Long): Instant

  fun hasReachedMaxRetries(retries: Long): Boolean

  fun handle(payload: String)

  fun handleFailure(payload: String)
}
