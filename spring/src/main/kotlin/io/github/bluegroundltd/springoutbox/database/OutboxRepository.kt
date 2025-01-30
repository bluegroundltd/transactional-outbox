package io.github.bluegroundltd.springoutbox.database

import io.github.bluegroundltd.outbox.item.OutboxStatus
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import java.time.Instant
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints

interface OutboxRepository : JpaRepository<OutboxItemEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(value = [QueryHint(name = "jakarta.persistence.lock.timeout", value = "100")])
  override fun findById(id: Long): Optional<OutboxItemEntity>

  @Query(
    value =
    """
      select * from outbox_item where
      (status = :pendingStatus and next_run < :nextRunLessThan)
      or
      (status = :runningStatus and rerun_after < :rerunAfterLessThan)
    """,
    nativeQuery = true
  )
  fun findByFilter(
    pendingStatus: String,
    nextRunLessThan: Instant,
    runningStatus: String,
    rerunAfterLessThan: Instant,
  ): List<OutboxItemEntity>

  // This could also be written as findAllByGroupIdInAndStatusNotIn(groupIds: Set<String>, statuses: Set<OutboxStatus>)
  // which is more generic (i.e. allowing a list of excluded statuses). However, its only current usage (in
  // [OutboxItemJpaDaoImpl#fetchNonCompletedByGroupIds]) always excludes a singe status values (COMPLETED).
  // Therefore, we made the decision to use the single field version to ensure that it will be as performant as possible
  // since it will be called quite often.
  // If the need for multiple excluded statuses arises, we should **strongly** consider refactoring this function instead
  // of creating a separate one.
  fun findAllByGroupIdInAndStatusNot(groupIds: Set<String>, status: OutboxStatus): List<OutboxItemEntity>

  fun deleteAllByDeleteAfterLessThanEqual(referenceTimestamp: Instant)
}
