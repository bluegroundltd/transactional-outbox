package io.github.bluegroundltd.springoutbox.event

import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
internal class AfterCommitEventListener(
  private val instantOutboxProcessor: InstantOutboxProcessor
) {

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  fun handle(event: AfterCommitApplicationEvent) =
    instantOutboxProcessor.processInstantOutbox(event.outboxItem)
}
