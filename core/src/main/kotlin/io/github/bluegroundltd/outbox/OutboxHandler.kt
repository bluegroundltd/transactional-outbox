package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType
import java.time.Duration
import java.time.Instant

/**
 * OutboxHandler is responsible for:
 * * handling the outbox item
 * * serializing the payload
 * * calculating the next execution time
 * * checking if the max retries has been reached
 * * handling the payload and
 * * handling the failure.
 *
 * Example:
 * ```
 * class MyOutboxHandler : OutboxHandler {
 *  override fun getSupportedType(): OutboxType {
 *    return MyOutboxType
 *  }
 *  override fun serialize(payload: OutboxPayload): String {
 *    return payload.toString()
 *  }
 *  override fun getNextExecutionTime(currentRetries: Long): Instant {
 *    return Instant.now()
 *  }
 *  override fun hasReachedMaxRetries(retries: Long): Boolean {
 *    return retries >= 3
 *  }
 *  override fun handle(payload: String) {
 *    // handle the payload
 *  }
 *  override fun handleFailure(payload: String) {
 *    // handle the failure
 *    // e.g. send an email to the admin
 *  }
 * }
 * ```
 */
interface OutboxHandler {

  /**
   * Returns the type of the outbox item that this handler can handle.
   */
  fun getSupportedType(): OutboxType

  /**
   * Serializes the payload into a string.
   *
   * @param payload the payload of the outbox item
   * @return the serialized payload
   */
  fun serialize(payload: OutboxPayload): String

  /**
   * Returns the next execution time for the outbox item.
   *
   * @param currentRetries the number of retries that have been performed
   * @return the next execution time
   */
  fun getNextExecutionTime(currentRetries: Long): Instant

  /**
   * Returns true if the outbox item has reached the maximum number of retries.
   *
   * @param retries the number of retries that have been performed
   * @return true if the outbox item has reached the maximum number of retries
   */
  fun hasReachedMaxRetries(retries: Long): Boolean

  /**
   * Handles the outbox item.
   *
   * @param payload the payload of the outbox item
   */
  fun handle(payload: String)

  /**
   * Handles the outbox item when it has reached the maximum number of retries.
   *
   * @param payload the payload of the outbox item
   */
  fun handleFailure(payload: String)

  /**
   * Returns the amount of time that the outbox items of this handler's type should be retained.
   * The outbox items will be deleted after this amount of time has passed after their completion.
   *
   * @return the retention duration of the outbox items
   */
  fun getRetentionDuration(): Duration
}
