package io.github.bluegroundltd.outbox.item

interface OutboxType {
  /**
   * The name of the outbox type.
   */
  fun getType(): String
}
