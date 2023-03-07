package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.store.OutboxFilter
import io.github.bluegroundltd.outbox.store.OutboxStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.EnumSet
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@SuppressWarnings("LongParameterList")
internal class TransactionalOutboxImpl(
  private val clock: Clock,
  private val outboxHandlers: Map<OutboxType, OutboxHandler>,
  private val locksProvider: OutboxLocksProvider,
  private val outboxStore: OutboxStore,
  private val rerunAfterDuration: Duration,
  private val executor: ExecutorService,
  private val decorator: OutboxItemProcessorDecorator? = null,
  private val threadPoolTimeOut: Duration
) : TransactionalOutbox {

  private var inShutdownMode = AtomicBoolean(false)

  companion object {
    private const val LOGGER_PREFIX = "[OUTBOX]"
    private val logger: Logger = LoggerFactory.getLogger(TransactionalOutboxImpl::class.java)
    private val STATUSES_ELIGIBLE_FOR_PROCESSING = EnumSet.of(OutboxStatus.PENDING, OutboxStatus.RUNNING)
  }

  override fun add(type: OutboxType, payload: OutboxPayload) {
    logger.info("$LOGGER_PREFIX Adding item of type: ${type.getType()} and payload: $payload")
    val handler = outboxHandlers[type]
      ?: throw UnsupportedOperationException("Outbox item type \"{${type.getType()}\" isn't supported")

    val outboxItem = makePendingItem(type, payload, handler)
    outboxStore.insert(outboxItem)
  }

  // TODO extract to factory
  private fun makePendingItem(type: OutboxType, payload: OutboxPayload, handler: OutboxHandler): OutboxItem {
    return OutboxItem(
      null,
      type,
      OutboxStatus.PENDING,
      handler.serialize(payload),
      0,
      handler.getNextExecutionTime(0),
      null,
      null
    )
  }

  override fun monitor() {
    if (inShutdownMode.get()) {
      logger.info("$LOGGER_PREFIX Shutdown in process - no longer accepting items for processing")
      return
    }

    runCatching {
      locksProvider.acquire()

      val items = fetchEligibleItems()
      if (items.isEmpty()) {
        logger.info("$LOGGER_PREFIX No outbox items to process")
      } else {
        logger.info("$LOGGER_PREFIX Will process ${items.size} outbox items")
      }

      markForProcessing(items)
      items.map { outboxStore.update(it) }

      items.forEach { item ->
        processItem(item)
      }
    }.onFailure {
      logger.error("$LOGGER_PREFIX Failure in monitor", it)
    }

    kotlin.runCatching { locksProvider.release() }.onFailure {
      logger.error("$LOGGER_PREFIX Failed to release lock of $locksProvider", it)
    }
  }

  private fun fetchEligibleItems(): List<OutboxItem> {
    val (eligibleItems, erroneouslyFetchedItems) = outboxStore
      .fetch(OutboxFilter(Instant.now(clock)))
      .partition { it.status in STATUSES_ELIGIBLE_FOR_PROCESSING }

    erroneouslyFetchedItems.forEach {
      logger.warn(
        "$LOGGER_PREFIX Outbox item with id ${it.id} erroneously fetched, as its status is ${it.status}. " +
          "Expected status to be one of $STATUSES_ELIGIBLE_FOR_PROCESSING"
      )
    }

    return eligibleItems
  }

  private fun markForProcessing(items: List<OutboxItem>) =
    items.map {
      it.status = OutboxStatus.RUNNING
      val now = Instant.now(clock)
      it.lastExecution = now
      it.rerunAfter = now.plus(rerunAfterDuration)
    }

  private fun processItem(item: OutboxItem) {
    val processor = decorate(OutboxItemProcessor(item, outboxHandlers[item.type]!!, outboxStore))
    try {
      executor.execute(processor)
    } catch (exception: RejectedExecutionException) {
      revertToPending(item)
      outboxStore.update(item)
    }
  }

  private fun decorate(processor: OutboxItemProcessor) =
    decorator?.decorate(processor) ?: processor

  private fun revertToPending(item: OutboxItem) {
    logger.info("$LOGGER_PREFIX Outbox item with id ${item.id} is reverting to PENDING")

    item.status = OutboxStatus.PENDING
    item.nextRun = Instant.now(clock)
    item.rerunAfter = null
  }

  override fun shutdown() {
    if (!inShutdownMode.compareAndSet(false, true)) {
      logger.info("$LOGGER_PREFIX Outbox shutdown already in progress")
      return
    }

    logger.info("$LOGGER_PREFIX Shutting down the outbox")
    executor.shutdown()
    val notExecutedRunnables =
      try {
        if (!executor.awaitTermination(threadPoolTimeOut.toSeconds(), TimeUnit.SECONDS)) {
          logger.debug("$LOGGER_PREFIX Forcing outbox shutdown")
          executor.shutdownNow()
        } else {
          logger.debug("$LOGGER_PREFIX All tasks executed")
          emptyList()
        }
      } catch (exception: Exception) {
        logger.warn("$LOGGER_PREFIX Shutdown failed.", exception)
        throw exception
      }

    notExecutedRunnables.filterIsInstance<OutboxItemProcessor>().forEach {
      val item = it.getItem()
      revertToPending(item)
      outboxStore.update(item)
    }
    logger.info("$LOGGER_PREFIX Outbox shutdown completed")
  }
}
