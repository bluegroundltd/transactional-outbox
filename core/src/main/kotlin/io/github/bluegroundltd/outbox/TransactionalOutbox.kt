package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType

/**
 * TransactionalOutbox is the main entry point for the library.
 * It is responsible for:
 * * adding outbox items (on demand or not)
 * * handling an on demand outbox
 * * monitoring the outbox items
 *
 * To instantiate a TransactionalOutbox, use the [TransactionalOutboxBuilder].
 *
 * Monitor function is to be called periodically to process the outbox items,
 * with a new transaction and a frequency of your choice.
 * It represents a polling consumer of the outbox items.
 *
 * Example:
 * ```
 * val outbox = TransactionalOutboxBuilder()
 *  .withStore(store)
 *  .withProcessor(processor)
 *  .build()
 *
 * outbox.addOutboxItem(MyOutboxPayload("id", "name"))
 *
 * @Scheduled(fixedRate = 1000)
 * fun processOutbox() {
 *   outbox.monitor()
 * }
 * ```
 */
sealed interface TransactionalOutbox {

  /**
   * Adds an outbox with will be processed on scheduled manner.
   *
   * @param type the type of the outbox item
   * @param payload the payload of the outbox item
   */
  fun add(type: OutboxType, payload: OutboxPayload)

  /**
   * Adds and outbox and emits an event to signal on-demand handling.
   *
   * @param type the type of the outbox item
   * @param payload the payload of the outbox item
   */
  fun addOnDemandOutbox(type: OutboxType, payload: OutboxPayload)

  /**
   * Will process the on-demand outbox, should be called by the
   * listener of the event emitted by [addOnDemandOutbox] and
   * in a separate transaction.
   *
   * Handles a specific on-demand outbox
   * @param outbox
   */
  fun handleOnDemandOutbox(outbox: OutboxItem)

  /**
   * Monitors the outbox for new items and processes them
   */
  fun monitor()
}
