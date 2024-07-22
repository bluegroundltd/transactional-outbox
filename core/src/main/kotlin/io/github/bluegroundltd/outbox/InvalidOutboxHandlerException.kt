package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.item.OutboxItem

internal data class InvalidOutboxHandlerException @JvmOverloads constructor(
  private val item: OutboxItem,
  override val message: String = formatDefaultMessage(item),
  override val cause: Throwable? = null
) : RuntimeException(message, cause) {
  companion object {
    private fun formatDefaultMessage(item: OutboxItem): String {
      return "Invalid Outbox Handler for item with id: ${item.id} and type: ${item.type}"
    }
  }
}
