package io.github.bluegroundltd.outbox.processing

import io.github.bluegroundltd.outbox.item.OutboxItem

internal data class OutboxHandlerException @JvmOverloads constructor(
  private val item: OutboxItem,
  override val cause: Throwable,
  override val message: String = formatDefaultMessage(item, cause),
) : RuntimeException(message, cause) {
  companion object {
    private fun formatDefaultMessage(item: OutboxItem, cause: Throwable) =
      "Handler for outbox: ${item.id} failed${formatCauseMessage(cause.message)}."

    private fun formatCauseMessage(message: String?): String =
      message?.let { " with message: '$it'" } ?: ""
  }
}
