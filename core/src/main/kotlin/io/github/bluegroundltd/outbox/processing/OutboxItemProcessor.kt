package io.github.bluegroundltd.outbox.processing

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.annotation.TestableOpenClass
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.store.OutboxStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant

@Suppress("TooGenericExceptionCaught")
@TestableOpenClass
internal class OutboxItemProcessor(
  private val item: OutboxItem,
  private val handlerResolver: (item: OutboxItem) -> OutboxHandler?,
  private val store: OutboxStore,
  private val clock: Clock,
) : OutboxProcessingAction {
  companion object {
    private const val LOGGER_PREFIX = "[OUTBOX-ITEM-PROCESSOR]"
    private val logger: Logger = LoggerFactory.getLogger(OutboxItemProcessor::class.java)
  }

  override fun run() {
    val handler = resolveHandler(item)

    try {
      logger.info("$LOGGER_PREFIX Handling item with id: ${item.id} and type: ${item.type}")
      handler.handle(item.payload)
      item.apply {
        status = OutboxStatus.COMPLETED
        deleteAfter = Instant.now(clock) + handler.getRetentionDuration()
      }
    } catch (exception: Exception) {
      if (handler.hasReachedMaxRetries(item.retries)) {
        handleTerminalFailure(handler, exception)
      } else {
        handleRetryableFailure(handler, exception)
      }
      throw exception
    } finally {
      store.update(item)
    }
  }

  /**
   * Performs required cleanup on the processor and resets the outbox item state so that it will be retried
   * on the next run.
   *
   * This method along with [run] present a concurrency risk since they are both modifying the state of
   * the outbox item. Therefore, it is the responsibility of the caller to ensure that it is called in
   * a thread-safe manner.
   *
   * Both callers of this method do indeed call it in a thread-safe manner. It's called in cases where
   * the processor has not started and will NOT be started. Specifically, if the execution was rejected
   * (inside [TransactionalOutboxImpl#processItem]) or a shutdown was requested by the executor service
   * and the processor did not have time to start (inside [TransactionalOutboxImpl#shutdown]).
   *
   * Also, it should be noted that the result of a potential concurrency issue, i.e. updating the item
   * twice and overwriting its values is not critical. The worst case scenario is that an item that has
   * been completed (or failed) may revert to pending state and be retried. While this is not ideal,
   * this has always been the case and due to the nature of the problem, cannot be completely avoided.
   * For example, there is always a chance that the processor is terminated immediately after the item
   * has been processed but before its state has been updated. Obviously, this is not related to a
   * concurrency issue, but still will result in the same outcome.
   *
   * We could add a synchronization mechanism (e.g. `synchronized`) to limit the issue in extreme cases
   * that are unrelated to concurrency, but we should consider the cost of introducing such a mechanism
   * against the rarity of the occurrence and the limited nature of its effect.
   */
  override fun reset() {
    if (item.status == OutboxStatus.RUNNING) {
      logger.info("$LOGGER_PREFIX Resetting outbox item with id: ${item.id} to PENDING")
      item.status = OutboxStatus.PENDING
      item.rerunAfter = null
      store.update(item)
    }
  }

  private fun resolveHandler(item: OutboxItem): OutboxHandler {
    val handler = handlerResolver(item)
    if (handler == null) {
      val message = "Handler could not be resolved for item with id: ${item.id} and type: ${item.type}"
      logger.error("$LOGGER_PREFIX $message")
      throw InvalidOutboxHandlerException(item, message)
    }
    if (!handler.supports(item.type)) {
      val message = "Handler ${handler::class.java} does not support item of type: ${item.type}"
      logger.error("$LOGGER_PREFIX $message")
      throw InvalidOutboxHandlerException(item, message)
    }
    return handler
  }

  private fun handleTerminalFailure(handler: OutboxHandler, exception: Exception) {
    logger.info(
      "$LOGGER_PREFIX Failure handling outbox item with id: ${item.id} and type: ${item.type}. " +
        "Item reached max-retries (${item.retries}), delegating failure to handler.",
      exception
    )
    item.status = OutboxStatus.FAILED
    handler.handleFailure(item.payload)
  }

  private fun handleRetryableFailure(handler: OutboxHandler, exception: Exception) {
    item.nextRun = handler.getNextExecutionTime(item.retries)
    item.retries += 1
    item.status = OutboxStatus.PENDING
    logger.info(
      "$LOGGER_PREFIX Failure handling outbox item with id: ${item.id} and type: ${item.type}. " +
        "Updated retries (${item.retries}) and next run is on ${item.nextRun}.",
      exception
    )
  }

  private fun OutboxHandler.supports(type: OutboxType) = this.getSupportedType() == type
}
