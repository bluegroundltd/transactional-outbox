package io.github.bluegroundltd.outbox.store

import io.github.bluegroundltd.outbox.item.OutboxItem
import java.time.Instant

/**
 * OutboxStore is responsible for storing and retrieving outbox items.
 */
interface OutboxStore {
  /**
   * Inserts an outbox item into the store.
   *
   * @param outboxItem the outbox item to insert
   * @return the inserted outbox item
   */
  fun insert(outboxItem: OutboxItem): OutboxItem

  /**
   * Updates an outbox item in the store.
   *
   * @param outboxItem the outbox item to update
   * @return the updated outbox item
   */
  fun update(outboxItem: OutboxItem): OutboxItem

  /**
   * Fetches outbox items from the store.
   *
   * @param outboxFilter the filter to apply to the outbox items
   * @return the list of outbox items
   */
  fun fetch(outboxFilter: OutboxFilter): List<OutboxItem>

  /**
   * Deletes completed outbox items from the store.
   *
   * @param now now
   */
  fun deleteCompletedItems(now: Instant)
}
