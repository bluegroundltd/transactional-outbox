package io.github.bluegroundltd.springoutbox

import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * This bean is just a kotlin wrapper for using transactional outbox.
 * Lazily injects the transactional outbox to avoid circular dependencies.
 * Should be used instead of [TransactionalOutbox] inside outbox handlers.
 * (no harm if used in other places as well)
 */
@Component
class TransactionalOutbox(
  @Lazy private val transactionalOutboxInstanceHolder: TransactionalOutboxInstanceHolder
) {

  fun add(type: OutboxType, payload: OutboxPayload, shouldPublishAfterInsertion: Boolean = false) =
    transactionalOutboxInstanceHolder.getTransactionalOutbox().add(type, payload, shouldPublishAfterInsertion)

  fun monitor(id: Long? = null) = transactionalOutboxInstanceHolder.getTransactionalOutbox().monitor(id)

  fun shutdown() = transactionalOutboxInstanceHolder.getTransactionalOutbox().shutdown()

  fun cleanup() = transactionalOutboxInstanceHolder.getTransactionalOutbox().cleanup()
}
