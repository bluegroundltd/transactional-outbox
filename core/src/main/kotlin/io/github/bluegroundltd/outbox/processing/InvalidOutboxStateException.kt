package io.github.bluegroundltd.outbox.processing

import io.github.bluegroundltd.outbox.item.OutboxItem

internal data class InvalidOutboxStateException @JvmOverloads constructor(
  private val item: OutboxItem,
  override val message: String = formatDefaultMessage(item),
  override val cause: Throwable? = null
) : RuntimeException(message, cause) {
  companion object {
    private fun formatDefaultMessage(item: OutboxItem) = "Invalid state for outbox: ${item.id}."
  }
}
