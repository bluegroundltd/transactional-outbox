package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType

/**
 * TransactionalOutbox is the main entry point for the library.
 * It is responsible for:
 * * adding outbox items (instant or not)
 * * handling an instant outbox
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
   * @param shouldPublishAfterInsertion flag that indicates if and event should be published
   *                                    to indicate that the created outbox should be processes
   *                                    immediately.
   */
  fun add(type: OutboxType, payload: OutboxPayload, shouldPublishAfterInsertion: Boolean = false)

  /**
   * Will process an instant outbox, should be called by the listener of
   * the event emitted by [add] with the `shouldPublishAfterInsertion` as true
   * and in a separate transaction.
   *
   * Handles a specific instant outbox
   * @param outbox
   */
  @Deprecated(
    message = "Deprecated in favor of using monitor with a hint (outbox item id)",
    replaceWith = ReplaceWith("monitor(outbox.id)")
  )
  fun processInstantOutbox(outbox: OutboxItem)

  /**
   * Monitors the outbox for new items and processes them
   *
   * @param id processes only the outbox item with this id instead of an eligible batch of items.
   *
   */
  fun monitor(id: Long? = null)

  fun monitor() = monitor(null)

  /**
   * Blocks new tasks and waits up to a specified period of time for all tasks to be completed.
   * If that time expires, the execution is stopped immediately.
   * Any tasks that did not start execution will have their corresponding item's status set to PENDING.
   * Shutdown is idempotent, so multiple invocations will have no additional effect.
   * Note that if the library is used in Spring, you may notice two invocations of shutdown, one from the
   * [@PreDestroy] and one via the automatic inference as described [here](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/Bean.html#destroyMethod()).
   */
  fun shutdown()

  /**
   * Deletes all the outbox items that have been completed and have gone past their retention duration.
   */
  fun cleanup()
}
