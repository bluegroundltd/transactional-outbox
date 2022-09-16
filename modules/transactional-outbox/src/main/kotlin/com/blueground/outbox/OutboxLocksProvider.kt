package com.blueground.outbox

/**
 * Expects a distributed locks provider implementation.
 *
 * The locks are to be used from multiple concurrent users of the library.
 */
interface OutboxLocksProvider {

  /**
   * Acquires a lock, waiting if it's not available (blocking).
   *
   * @param id The identifier for the lock
   */
  fun acquire(id: Long)

  /**
   * Release the lock associated with the id.
   *
   * @param id The identifier of the lock to be released
   */
  fun release(id: Long)
}
