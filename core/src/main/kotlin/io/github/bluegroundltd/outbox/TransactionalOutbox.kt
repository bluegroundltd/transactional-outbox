package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType

/**
 * TransactionalOutbox is the main entry point for the library.
 * It is responsible for:
 * * adding outbox items
 * * monitoring the outbox items
 *
 * To instanciate a TransactionalOutbox, use the [TransactionalOutboxBuilder].
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
   * @param type the type of the outbox item
   * @param payload the payload of the outbox item
   */
  fun add(type: OutboxType, payload: OutboxPayload)

  /**
   * Monitors the outbox for new items and processes them
   */
  fun monitor()
}
