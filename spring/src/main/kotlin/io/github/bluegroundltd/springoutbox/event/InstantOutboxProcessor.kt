package io.github.bluegroundltd.springoutbox.event

import io.github.bluegroundltd.springoutbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.item.OutboxItem
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
internal class InstantOutboxProcessor(
  private val transactionalOutbox: TransactionalOutbox
) {

  @Async
  @Transactional
  fun processInstantOutbox(outboxItem: OutboxItem) =
    transactionalOutbox.monitor(outboxItem.id)
}
