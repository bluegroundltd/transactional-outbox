package com.blueground.outbox

import com.blueground.outbox.item.OutboxItem
import com.blueground.outbox.item.OutboxStatus
import com.blueground.outbox.store.OutboxStore

class OutboxItemProcessor(
  private val item: OutboxItem,
  private val handler: OutboxHandler,
  private val store: OutboxStore
) : Runnable {

  override fun run() {
    if (!handler.supports(item.type)) {
      throw IllegalArgumentException("Handler ${handler::class.java} does not support item of type ${item.type}")
    }

    try {
      handler.handle(item.payload)
      item.status = OutboxStatus.COMPLETED
    } catch (_: Exception) {
      // TODO log exception or pass it to handler (for FAILED items)
      handleGracefulFailure()
    } finally {
      store.update(item)
    }
  }

  private fun handleGracefulFailure() {
    if (handler.hasReachedMaxRetries(item.retries)) {
      item.status = OutboxStatus.FAILED
      handler.handleFailure(item.payload)
    } else {
      item.retries += 1
      item.nextRun = handler.getNextExecutionTime(item.retries)
    }
  }
}
