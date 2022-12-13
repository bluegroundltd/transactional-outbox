package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant

/**
 * This class purpose is to create an easy way to create an outbox handler.
 *
 * In order to achieve this, the following assumputions are made:
 * 1. Object mapper is used for serializing and deserializing outbox
 *  (Before invoking [handleWithParsedPayload] the object mapper deserializer is invoked)
 * 2. Linear retries are applied 1 minute from now (clock is used)
 *
 * Note: This is a quick solution to avoid duplicate code. It should be extracted to support
 * multiple retries policies and support configurable serialize.
 *
 */
abstract class SimpleOutboxHandler<T : OutboxPayload>(
  private val supportedType: OutboxType,
  private val objectMapper: ObjectMapper,
  private val clock: Clock,
  private val maxRetries: Int,
  private val payloadType: Class<T>,
) : OutboxHandler {

  companion object {
    private const val ONE_MINUTE_IN_SECONDS = 60L
  }

  override fun getSupportedType(): OutboxType {
    return supportedType
  }

  override fun serialize(payload: OutboxPayload): String =
    objectMapper.writeValueAsString(payload)

  override fun getNextExecutionTime(currentRetries: Long): Instant = when (currentRetries) {
    0L -> Instant.now(clock)
    else -> Instant.now(clock).plusSeconds(ONE_MINUTE_IN_SECONDS)
  }

  override fun hasReachedMaxRetries(retries: Long): Boolean =
    retries >= maxRetries

  override fun handle(payload: String) {
    val payloadParsed = objectMapper.readValue(payload, payloadType)
    handleWithParsedPayload(payloadParsed)
  }

  abstract fun handleWithParsedPayload(payload: T)
}
