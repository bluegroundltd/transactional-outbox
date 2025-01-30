package io.github.bluegroundltd.springoutbox.event

import io.github.bluegroundltd.outbox.item.OutboxItem
import org.springframework.context.ApplicationEvent

class AfterCommitApplicationEvent(
  source: Any,
  val outboxItem: OutboxItem
) : ApplicationEvent(source)
