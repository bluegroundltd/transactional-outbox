package com.blueground.outbox

import com.blueground.outbox.item.OutboxItem
import com.blueground.outbox.item.OutboxPayload
import com.blueground.outbox.item.OutboxStatus
import com.blueground.outbox.item.OutboxType
import com.blueground.outbox.store.OutboxFilter
import com.blueground.outbox.store.OutboxStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.EnumSet
import java.util.concurrent.ExecutorService

@SuppressWarnings("LongParameterList")
internal class TransactionalOutboxImpl(
  private val clock: Clock,
  private val outboxHandlers: Map<OutboxType, OutboxHandler>,
  private val lockIdentifier: Long,
  private val locksProvider: OutboxLocksProvider,
  private val outboxStore: OutboxStore,
  private val rerunAfterDuration: Duration,
  private val executor: ExecutorService
) : TransactionalOutbox {

  companion object {
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

    erroneouslyFetchedItems.forEach {
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
      it.rerunAfter = it.lastExecution?.plus(rerunAfterDuration)
    }
}
