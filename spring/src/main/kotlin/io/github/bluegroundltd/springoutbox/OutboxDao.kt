package io.github.bluegroundltd.springoutbox

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.store.OutboxFilter
import java.time.Instant

interface OutboxDao {
  fun findByIdAndLock(id: Long): OutboxItem?
  fun fetchByFilter(outboxFilter: OutboxFilter): List<OutboxItem>
  fun fetchNonCompletedByGroupIds(groupIds: Iterable<String>, status: OutboxStatus): List<OutboxItem>
  fun deleteAllByDeleteAfterLessThanEqual(referenceTimestamp: Instant)
  fun save(outboxItem: OutboxItem): OutboxItem
  fun saveAndFlush(outboxItem: OutboxItem): OutboxItem
}
