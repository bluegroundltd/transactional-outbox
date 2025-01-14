package io.github.bluegroundltd.springoutbox.database

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.store.OutboxFilter
import java.time.Instant
import org.springframework.core.convert.ConversionService
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
internal class OutboxDaoImpl(
  private val outboxRepository: OutboxRepository,
  private val conversionService: ConversionService,
) : OutboxDao {
  override fun findByIdAndLock(id: Long): OutboxItem? =
    outboxRepository.findById(id).orElse(null)
      .let { conversionService.convert(it, OutboxItem::class.java) }

  override fun fetchByFilter(outboxFilter: OutboxFilter): List<OutboxItem> =
    outboxRepository.findByFilter(
      outboxFilter.outboxPendingFilter.status.name,
      outboxFilter.outboxPendingFilter.nextRunLessThan,
      outboxFilter.outboxRunningFilter.status.name,
      outboxFilter.outboxRunningFilter.rerunAfterLessThan,
    ).map { conversionService.convert(it, OutboxItem::class.java)!! }

  override fun fetchNonCompletedByGroupIds(groupIds: Iterable<String>, status: OutboxStatus): List<OutboxItem> =
    outboxRepository.findAllByGroupIdInAndStatusNot(groupIds.toSet(), status)
      .map { conversionService.convert(it, OutboxItem::class.java)!! }

  override fun deleteAllByDeleteAfterLessThanEqual(referenceTimestamp: Instant) =
    outboxRepository.deleteAllByDeleteAfterLessThanEqual(referenceTimestamp)

  @Transactional(propagation = Propagation.MANDATORY)
  override fun save(outboxItem: OutboxItem): OutboxItem {
    val outboxItemEntity = findOrCreateOutboxItemEntity(outboxItem)
    return outboxRepository.save(outboxItemEntity)
      .let { conversionService.convert(it, OutboxItem::class.java)!! }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  override fun saveAndFlush(outboxItem: OutboxItem): OutboxItem {
    val outboxItemEntity = findOrCreateOutboxItemEntity(outboxItem)
    return outboxRepository.saveAndFlush(outboxItemEntity)
      .let { conversionService.convert(it, OutboxItem::class.java)!! }
  }

  /*
    * Instead of creating a new entity every time, we need to check based on the id if the entity already exists
    * in the hibernate persistence context first. Otherwise, the entity will be considered detached.
   */
  private fun findOrCreateOutboxItemEntity(outboxItem: OutboxItem): OutboxItemEntity =
    if (outboxItem.id != null) {
      val outboxItemEntity = outboxRepository.findById(outboxItem.id!!).orElseThrow()
      outboxItemEntity.apply {
        status = outboxItem.status
        retries = outboxItem.retries
        nextRun = outboxItem.nextRun
        lastExecution = outboxItem.lastExecution
        rerunAfter = outboxItem.rerunAfter
        deleteAfter = outboxItem.deleteAfter
      }
    } else {
      conversionService.convert(outboxItem, OutboxItemEntity::class.java)!!
    }
}
