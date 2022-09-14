package com.blueground.outbox

import com.blueground.outbox.item.OutboxItem
import com.blueground.outbox.item.OutboxPayload
import com.blueground.outbox.item.OutboxStatus
import com.blueground.outbox.item.OutboxType
import com.blueground.outbox.store.OutboxFilter
import com.blueground.outbox.store.OutboxStore
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.EnumSet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TransactionalOutboxImpl(
  private val clock: Clock,
  private val outboxHandlers: Map<OutboxType, OutboxHandler>,
  private val lockIdentifier: Long,
  private val locksProvider: OutboxLocksProvider,
  private val outboxStore: OutboxStore,
  private val executor: ExecutorService = Executors.newFixedThreadPool(
    DEFAULT_THREAD_POOL_SIZE,
    ThreadFactoryBuilder().setNameFormat(THEAD_POOL_NAME_FORMAT).build()
  )
) : TransactionalOutbox {

  companion object {
    private val RERUN_AFTER_DEFAULT_DURATION = Duration.ofHours(1)
    private const val DEFAULT_THREAD_POOL_SIZE = 10
    private const val THEAD_POOL_NAME_FORMAT = "outbox-item-processor-%d"
    private val logger: Logger = LoggerFactory.getLogger(TransactionalOutboxImpl::class.java)
    private val STATUSES_ELIGIBLE_FOR_PROCESSING = EnumSet.of(OutboxStatus.PENDING, OutboxStatus.RUNNING)
  }

  override fun add(type: OutboxType, payload: OutboxPayload) {
    logger.info("Adding item of type: ${type.getType()} and payload: $payload")
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
    locksProvider.acquire(lockIdentifier)
    val items = fetchEligibleItems()
    markForProcessing(items)
    items.map { outboxStore.update(it) }
    locksProvider.release(lockIdentifier)

    items.forEach { item ->
      executor.execute(
        OutboxItemProcessor(item, outboxHandlers[item.type]!!, outboxStore)
      )
    }
  }

  private fun fetchEligibleItems(): List<OutboxItem> {
    val (eligibleItems, erroneouslyFetchedItems) = outboxStore
      .fetch(OutboxFilter(Instant.now(clock)))
      .partition { it.status in STATUSES_ELIGIBLE_FOR_PROCESSING }

    erroneouslyFetchedItems.map {
      logger.warn(
        "Outbox item with id ${it.id} erroneously fetched, as its status is ${it.status}. " +
          "Expected status to be one of $STATUSES_ELIGIBLE_FOR_PROCESSING"
      )
    }

    return eligibleItems
  }

  private fun markForProcessing(items: List<OutboxItem>) =
    items.map {
      it.status = OutboxStatus.RUNNING
      it.lastExecution = Instant.now(clock)
      it.rerunAfter = it.lastExecution?.plus(RERUN_AFTER_DEFAULT_DURATION)
    }
}
