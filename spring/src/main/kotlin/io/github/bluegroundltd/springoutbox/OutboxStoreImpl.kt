package io.github.bluegroundltd.springoutbox

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.store.OutboxFilter
import io.github.bluegroundltd.outbox.store.OutboxStore
import io.github.bluegroundltd.outbox.store.OutboxStoreInsertHints
import io.github.bluegroundltd.springoutbox.database.OutboxDao
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
internal class OutboxStoreImpl(
  private val outboxDao: OutboxDao,
) : OutboxStore {

  companion object {
    private const val LOGGER_PREFIX = "[TRANSACTIONAL-OUTBOX-STORE]"
    private val logger: Logger = LoggerFactory.getLogger(OutboxStoreImpl::class.java)

    private val defaultInsertHints =
      OutboxStoreInsertHints(forInstantProcessing = false, instantOrderingEnabled = false)
  }

  override fun insert(outboxItem: OutboxItem): OutboxItem = insert(outboxItem, defaultInsertHints)

  override fun insert(outboxItem: OutboxItem, hints: OutboxStoreInsertHints): OutboxItem {
    val savedEntity = if (hints.shouldFlushAfterInsertion()) {
      outboxDao.saveAndFlush(outboxItem)
    } else {
      outboxDao.save(outboxItem)
    }
    logger.debug("$LOGGER_PREFIX@[${Thread.currentThread().name}] Inserted outbox item with id: ${savedEntity.id}")
    return savedEntity
  }

  private fun OutboxStoreInsertHints.shouldFlushAfterInsertion(): Boolean =
    forInstantProcessing && !instantOrderingEnabled

  @Transactional
  override fun update(outboxItem: OutboxItem): OutboxItem {
    val thread = Thread.currentThread()

    logger.debug(
      "$LOGGER_PREFIX@[${thread.name}] Retrieving outbox item with id: ${outboxItem.id}"
    )
    // The lock here is not really used that for protecting against concurrent updates, since the same protection would have been provided
    // by the `save` statement. However, it does allow for a 'waiting' period which in most cases is more than enough for other threads to
    // finish up / commit any transactions. Which in turn, allows for fetching fully up-to-date data and then for the update to go through.
    val savedOutboxItem = outboxDao.findByIdAndLock(outboxItem.id!!)
      ?: throw IllegalArgumentException("Failed to update outbox item with id ${outboxItem.id} as it doesn't exist.")

    if (savedOutboxItem.groupId != outboxItem.groupId) {
      logger.warn(
        "$LOGGER_PREFIX Updating group id is not supported: outbox: ${outboxItem.id}, " +
            "existing value: ${savedOutboxItem.groupId}, new value: ${outboxItem.groupId}."
      )
    }

    savedOutboxItem.apply {
      status = outboxItem.status
      retries = outboxItem.retries
      nextRun = outboxItem.nextRun
      lastExecution = outboxItem.lastExecution
      rerunAfter = outboxItem.rerunAfter
      deleteAfter = outboxItem.deleteAfter
    }

    logger.debug(
      "$LOGGER_PREFIX@[${thread.name}] Updating outbox item with id: ${savedOutboxItem.id}"
    )
    val savedEntity = outboxDao.save(savedOutboxItem)
    logger.debug(
      "$LOGGER_PREFIX@[${thread.name}] Updated outbox item with id: ${savedEntity.id}"
    )

    return savedEntity
  }

  override fun fetch(outboxFilter: OutboxFilter): List<OutboxItem> =
    outboxDao.fetchByFilter(outboxFilter)
      .enhanceWithSameGroupItems()

  private fun List<OutboxItem>.enhanceWithSameGroupItems(): List<OutboxItem> {
    val groupIds = mapNotNull { it.groupId }
    return if (groupIds.isEmpty()) {
      this
    } else {
      val sameGroupItems = fetchItemsWithGroupId(groupIds)
      (this + sameGroupItems).distinctBy { it.id }
    }
  }

  private fun fetchItemsWithGroupId(groupIds: Iterable<String>): List<OutboxItem> {
    val items = outboxDao.fetchNonCompletedByGroupIds(groupIds.toSet(), OutboxStatus.COMPLETED)
    return items.distinctBy { it.id }
  }

  override fun deleteCompletedItems(now: Instant) {
    outboxDao.deleteAllByDeleteAfterLessThanEqual(now)
  }
}
