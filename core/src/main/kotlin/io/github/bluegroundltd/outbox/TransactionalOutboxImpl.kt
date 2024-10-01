package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.event.InstantOutboxEvent
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.grouping.OutboxGroupingProvider
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxItemGroup
import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.item.factory.OutboxItemFactory
import io.github.bluegroundltd.outbox.processing.OutboxGroupProcessor
import io.github.bluegroundltd.outbox.processing.OutboxItemProcessorDecorator
import io.github.bluegroundltd.outbox.processing.OutboxProcessingHost
import io.github.bluegroundltd.outbox.processing.OutboxProcessingHostComposer
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

@SuppressWarnings("LongParameterList", "TooGenericExceptionCaught")
internal class TransactionalOutboxImpl(
  private val clock: Clock,
  private val outboxHandlers: Map<OutboxType, OutboxHandler>,
  private val monitorLocksProvider: OutboxLocksProvider,
  private val cleanupLocksProvider: OutboxLocksProvider,
  private val outboxStore: OutboxStore,
  private val instantOutboxPublisher: InstantOutboxPublisher,
  private val outboxItemFactory: OutboxItemFactory,
  private val rerunAfterDuration: Duration,
  private val executor: ExecutorService,
  private val decorators: List<OutboxItemProcessorDecorator> = emptyList(),
  private val threadPoolTimeOut: Duration,
  private val processingHostComposer: OutboxProcessingHostComposer,
  private val instantOrderingEnabled: Boolean,
  private val groupingProvider: OutboxGroupingProvider
) : TransactionalOutbox {

  private var inShutdownMode = AtomicBoolean(false)

  companion object {
    private const val LOGGER_PREFIX = "[OUTBOX]"
    private val logger: Logger = LoggerFactory.getLogger(TransactionalOutboxImpl::class.java)
    private val STATUSES_RETRIEVED_FOR_PROCESSING = EnumSet.of(
      OutboxStatus.PENDING,
      OutboxStatus.RUNNING,
      OutboxStatus.FAILED // Required for in-order processing in groups (i.e. they might precede other items).
    )
  }

  override fun add(type: OutboxType, payload: OutboxPayload, shouldPublishAfterInsertion: Boolean) {
    logger.info("$LOGGER_PREFIX Adding item of type: ${type.getType()} and payload: $payload")

    // Build scheduled outbox item and insert it. Alternatively, for instant outboxes we could prepare for
    // processing before inserting it. But then we would need to devise a method to allow us for updating
    // `markedForProcessing` based on the originally built item.
    val outboxItem = outboxItemFactory.makeScheduledOutboxItem(type, payload)
      .run { outboxStore.insert(this) }

    if (shouldPublishAfterInsertion) {
      if (!instantOrderingEnabled) {
        // When instant ordering is not enabled (i.e. legacy instant processing), the item will be used from the
        // processing method (i.e. `processInstantOutbox`) directly (i.e. will not be retrieved from the database).
        // Therefore, we need to prepare/update it for processing so that any other possible concurrent operations
        // do not pick it up. (This is essentially what `monitor` also does.)
        val now = Instant.now(clock)
        outboxItem.updateForProcessing(now, now + rerunAfterDuration)
      }
      instantOutboxPublisher.publish(InstantOutboxEvent(outbox = outboxItem))
    }
  }

  @Deprecated(
    message = "Deprecated in favor of using monitor with a hint (outbox item id)",
    replaceWith = ReplaceWith("monitor(outbox.id)")
  )
  override fun processInstantOutbox(outbox: OutboxItem) {
    logger.info("$LOGGER_PREFIX Instant processing of \"${outbox.type.getType()}\" outbox")
    if (instantOrderingEnabled) {
      monitor(outbox.id)
    } else {
      runCatching {
        val processor = OutboxGroupProcessor(OutboxItemGroup.of(outbox), ::resolveOutboxHandler, outboxStore, clock)
        val processingHost = processingHostComposer.compose(processor, decorators)
        executor.execute(processingHost)
      }.onFailure {
        logger.error("$LOGGER_PREFIX Failure in instant handling", it)
      }
    }
  }

  override fun monitor(id: Long?) {
    if (inShutdownMode.get()) {
      logger.info("$LOGGER_PREFIX Shutdown in process, no longer accepting items for processing")
      return
    }

    runCatching {
      monitorLocksProvider.acquire()

      val fetchedItems = fetchEligibleItems(id)
      if (fetchedItems.isEmpty()) {
        logger.info("$LOGGER_PREFIX No outbox items to process")
      } else {
        logger.info("$LOGGER_PREFIX Will process ${fetchedItems.size} outbox items")
      }

      fetchedItems
        .map { it.copy() } // Defensive copy to avoid external modifications.
        .group()
        .filterByIdIfExists(id) // ensures item filtering regardless of client's `fetch`
        .apply { updateForProcessing() } // mark groups (essentially contained items) for processing
        .forEach { it.process() }
    }.onFailure {
      logger.error("$LOGGER_PREFIX Failure in monitor", it)
    }

    runCatching { monitorLocksProvider.release() }.onFailure {
      logger.error("$LOGGER_PREFIX Failed to release lock of $monitorLocksProvider", it)
    }
  }

  private fun fetchEligibleItems(id: Long?): List<OutboxItem> {
    val (eligibleItems, erroneouslyFetchedItems) = outboxStore
      .fetch(
        OutboxFilter(
          nextRunLessThan = Instant.now(clock),
          id = id
        )
      )
      .partition { it.status in STATUSES_RETRIEVED_FOR_PROCESSING }

    erroneouslyFetchedItems.forEach {
      logger.warn(
        "$LOGGER_PREFIX Outbox item with id ${it.id} erroneously fetched, as its status is ${it.status}. " +
          "Expected status to be one of $STATUSES_RETRIEVED_FOR_PROCESSING"
      )
    }

    return eligibleItems
  }

  private fun List<OutboxItemGroup>.updateForProcessing() {
    val now = Instant.now(clock)
    val rerunAfter = now.plus(rerunAfterDuration)
    flatMap { it.items }.onEach { it.updateForProcessing(now, rerunAfter) }
  }

  private fun OutboxItem.updateForProcessing(now: Instant, rerunAfter: Instant) {
    // It is important to NOT return the item returned from `update` since it may be a different instance
    // where the `markedForProcessing` flag is not properly set.
    // Rerunning `prepareForProcessing` on it, wouldn't work since **by design** (i.e. when retrieving items
    // that have been updated for processing from another thread / instance) the function will not work on them.
    prepareForProcessing(now, rerunAfter)
    outboxStore.update(this)
  }

  private fun List<OutboxItem>.group(): List<OutboxItemGroup> = groupingProvider.execute(this)

  private fun OutboxItemGroup.process() {
    val processor = OutboxGroupProcessor(this, ::resolveOutboxHandler, outboxStore, clock)
    val processingHost = processingHostComposer.compose(processor, decorators)
    try {
      executor.execute(processingHost)
    } catch (exception: RejectedExecutionException) {
      val ids = items.joinToString(", ") { it.id.toString() }
      logger.info("$LOGGER_PREFIX Executor rejected processing of outbox group with items: $ids")
      processingHost.reset()
    }
  }

  private fun resolveOutboxHandler(item: OutboxItem): OutboxHandler? = outboxHandlers[item.type]

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

    notExecutedRunnables.filterIsInstance<OutboxProcessingHost>().forEach {
      it.reset()
    }
    logger.info("$LOGGER_PREFIX Outbox shutdown completed")
  }

  override fun cleanup() {
    if (inShutdownMode.get()) {
      logger.info("$LOGGER_PREFIX Shutdown in process, deferring cleanup")
      return
    }

    var wasLockingAcquired = false
    try {
      cleanupLocksProvider.acquire()
      wasLockingAcquired = true

      val now = Instant.now(clock)
      logger.info("$LOGGER_PREFIX Cleaning up completed outbox items, with deleteAfter <= $now")
      outboxStore.deleteCompletedItems(now)
    } catch (exception: Exception) {
      logger.error("$LOGGER_PREFIX Failure in cleanup", exception)
    } finally {
      if (wasLockingAcquired) {
        try {
          cleanupLocksProvider.release()
        } catch (exception: Exception) {
          logger.error("$LOGGER_PREFIX Failed to release cleanup lock ($cleanupLocksProvider)", exception)
        }
      }
    }
  }

  private fun List<OutboxItemGroup>.filterByIdIfExists(id: Long?): List<OutboxItemGroup> =
    id?.let { filter { it.contains(id) } } ?: this

  private fun OutboxItemGroup.contains(id: Long): Boolean =
    items.any { it.id == id }
}
