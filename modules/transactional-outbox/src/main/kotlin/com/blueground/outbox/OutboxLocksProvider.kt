package com.blueground.outbox

/**
 * Expects a distributed locks provider implementation.
 *
 * The locks are to be used from multiple concurrent users of the library.
 */
interface OutboxLocksProvider {

  /**
   * Acquires a lock, waiting if it's not available (blocking).
   */
  fun acquire()

  /**
   * Release the lock associated with the id.
   */
  fun release()
}
