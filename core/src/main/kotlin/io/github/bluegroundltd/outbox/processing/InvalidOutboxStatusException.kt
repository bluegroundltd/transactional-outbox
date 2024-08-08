package io.github.bluegroundltd.outbox.processing

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus

internal data class InvalidOutboxStatusException @JvmOverloads constructor(
  private val item: OutboxItem,
  private val expectedStatues: Set<OutboxStatus> = emptySet(),
  override val message: String = formatDefaultMessage(item, expectedStatues),
  override val cause: Throwable? = null
) : RuntimeException(message, cause) {
  companion object {
    private fun formatDefaultMessage(item: OutboxItem, expectedStatues: Set<OutboxStatus>) =
      "Invalid status: ${item.status} for outbox: ${item.id}.${formatExpectedStatusesFragment(expectedStatues)}"

    private fun formatExpectedStatusesFragment(expectedStatues: Set<OutboxStatus>) =
      if (expectedStatues.isNotEmpty()) {
        " Expected one of: ${expectedStatues.joinToString()}."
      } else {
        ""
      }
  }
}
