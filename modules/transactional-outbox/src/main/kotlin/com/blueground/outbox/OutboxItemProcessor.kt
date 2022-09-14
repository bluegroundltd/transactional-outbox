package com.blueground.outbox

import com.blueground.outbox.item.OutboxItem
import com.blueground.outbox.item.OutboxStatus
import com.blueground.outbox.store.OutboxStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OutboxItemProcessor(
  private val item: OutboxItem,
  private val handler: OutboxHandler,
  private val store: OutboxStore
) : Runnable {

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(OutboxItemProcessor::class.java)
  }

  override fun run() {
    if (!handler.supports(item.type)) {
      throw IllegalArgumentException("Handler ${handler::class.java} does not support item of type ${item.type}")
    }

    try {
      handler.handle(item.payload)
      item.status = OutboxStatus.COMPLETED
    } catch (exception: Exception) {
      if (handler.hasReachedMaxRetries(item.retries)) {
        handleTerminalFailure(exception)
      } else {
        handleRetryableFailure(exception)
      }
    } finally {
      store.update(item)
    }
  }

  private fun handleTerminalFailure(exception: Exception) {
    logger.info(
      "Failure handling outbox item with id: ${item.id} and type: ${item.type}. " +
        "Item reached max-retries (${item.retries}), delegating failure to handler.",
      exception
    )
    item.status = OutboxStatus.FAILED
    handler.handleFailure(item.payload)
  }

  private fun handleRetryableFailure(exception: Exception) {
    item.retries += 1
    item.nextRun = handler.getNextExecutionTime(item.retries)
    logger.info(
      "Failure handling outbox item with id: ${item.id} and type: ${item.type}. " +
        "Updated retries (${item.retries}) and next run is on ${item.nextRun}.",
      exception
    )
  }
}
