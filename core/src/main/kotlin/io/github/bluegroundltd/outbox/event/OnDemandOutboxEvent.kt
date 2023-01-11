package io.github.bluegroundltd.outbox.event

import io.github.bluegroundltd.outbox.item.OutboxItem

/**
 * Event emitted upon the addition of an on demand outbox.
 *
 * The concrete implementation of [OnDemandOutboxPublisher] supplied by the caller
 * will handle the event.
 */
class OnDemandOutboxEvent(
  val outbox: OutboxItem,
)
