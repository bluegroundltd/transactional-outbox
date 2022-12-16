package io.github.bluegroundltd.outbox.item

/**
 * OutboxStatus is the processing status of an outbox item.
 */
enum class OutboxStatus {
  /**
   * The item is ready to be processed.
   */
  PENDING,

  /**
   * The item is currently being processed.
   */
  RUNNING,

  /**
   * The item has been processed successfully.
   */
  COMPLETED,

  /**
   * The item has failed to be processed.
   */
  FAILED
}
