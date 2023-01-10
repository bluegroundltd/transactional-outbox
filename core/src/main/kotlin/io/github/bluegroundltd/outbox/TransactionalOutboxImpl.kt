package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.event.OnDemandOutboxEvent
import io.github.bluegroundltd.outbox.event.OnDemandOutboxPublisher
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.item.factory.OutboxItemFactory
import io.github.bluegroundltd.outbox.store.OutboxFilter
import io.github.bluegroundltd.outbox.store.OutboxStore
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.EnumSet
import java.util.concurrent.ExecutorService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@SuppressWarnings("LongParameterList")
internal class TransactionalOutboxImpl(
  private val clock: Clock,
  private val outboxHandlers: Map<OutboxType, OutboxHandler>,
  private val locksProvider: OutboxLocksProvider,
  private val outboxStore: OutboxStore,
  private val onDemandOutboxPublisher: OnDemandOutboxPublisher,
  private val outboxItemFactory: OutboxItemFactory,
  private val rerunAfterDuration: Duration,
  private val executor: ExecutorService,
) : TransactionalOutbox {

  companion object {
    private const val LOGGER_PREFIX = "[OUTBOX]"
    private val logger: Logger = LoggerFactory.getLogger(TransactionalOutboxImpl::class.java)
    private val STATUSES_ELIGIBLE_FOR_PROCESSING = EnumSet.of(OutboxStatus.PENDING, OutboxStatus.RUNNING)
  }

  override fun add(type: OutboxType, payload: OutboxPayload) {
    logger.info("$LOGGER_PREFIX Adding item of type: ${type.getType()} and payload: $payload")

    val outboxItem = outboxItemFactory.makeScheduledOutboxItem(type, payload)
    outboxStore.insert(outboxItem)
  }

  override fun addOnDemandOutbox(type: OutboxType, payload: OutboxPayload) {
    logger.info("$LOGGER_PREFIX Adding item of type: ${type.getType()} and payload: $payload")

    val outboxItem = outboxItemFactory.makeOnDemandOutboxItem(type, payload)
    outboxStore.insert(outboxItem)
      .also {
        onDemandOutboxPublisher.publish(OnDemandOutboxEvent(outbox = it))
      }
  }

  override fun handleOnDemandOutbox(outbox: OutboxItem) {
    runCatching {
      logger.info("$LOGGER_PREFIX On demand processing of \"${outbox.type.getType()}\" outbox")
      executor.execute(
        OutboxItemProcessor(outbox, outboxHandlers[outbox.type]!!, outboxStore)
      )
    }.onFailure {
      logger.error("$LOGGER_PREFIX Failure in on demand handling", it)
    }
  }

  override fun monitor() {
    runCatching {
      locksProvider.acquire()

      val items = fetchEligibleItems()
      if (items.isEmpty()) {
        logger.info("$LOGGER_PREFIX No outbox items to process")
      } else {
        logger.info("$LOGGER_PREFIX Will process ${items.size} outbox items")
      }

      markForProcessing(items)
        .map { outboxStore.update(it) }
        .forEach { item ->
          executor.execute(
            OutboxItemProcessor(item, outboxHandlers[item.type]!!, outboxStore)
          )
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

  private fun markForProcessing(items: List<OutboxItem>): List<OutboxItem> {
    val now = Instant.now(clock)
    return items.map {
      it.copy(
        status = OutboxStatus.RUNNING,
        lastExecution = now,
        rerunAfter = now.plus(rerunAfterDuration)
      )
    }
  }
}
