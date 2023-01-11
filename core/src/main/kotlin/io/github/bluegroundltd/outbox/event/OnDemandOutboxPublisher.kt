package io.github.bluegroundltd.outbox.event

import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxBuilder

/**
 * The publisher will emit an event upon the creation of the on demand outbox.
 * Having an event emitted upon the outbox creation enables us handle an on demand outbox
 * processing in separate transactions. We can listen for the published event after
 * transaction is committed, and then we can process the outbox
 * by calling [TransactionalOutbox.handleOnDemandOutbox].
 *
 * The implementation is specific to the framework that uses the library so the
 * caller needs to provide the specifics.
 *
 * The publisher is required so that an instance of [TransactionalOutbox] is created
 * using the [TransactionalOutboxBuilder].
 *
 * Example of the publisher implementation on Spring framework:
 * ```kotlin
 * class OnDemandOutboxPublisherImpl(
 *   private val applicationEventPublisher: ApplicationEventPublisher,
 * ) : OnDemandOutboxPublisher {
 *
 * override fun publish(event: OnDemandOutboxEvent) =
 *   applicationEventPublisher.publishEvent(
 *     SpringApplicationEvent(
 *       source = this,
 *       payload = event.outbox)
 *     )
 * }
 * ```
 *
 * Example of a listener on Spring framework:
 * ```kotlin
 * class AfterCommitEventListener(
 *   private val transactionalOutbox: TransactionalOutbox
 * ) {
 *
 *   @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 *   fun handle(event: SpringApplicationEvent) {
 *     transactionalOutbox.handleOnDemandOutbox(event.payload)
 *   }
 * }
 * ```
 */
interface OnDemandOutboxPublisher {
  fun publish(event: OnDemandOutboxEvent)
}
