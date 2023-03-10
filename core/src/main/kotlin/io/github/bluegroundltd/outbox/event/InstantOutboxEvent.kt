package io.github.bluegroundltd.outbox.event

import io.github.bluegroundltd.outbox.item.OutboxItem

/**
 * Event emitted upon the addition of an instant outbox.
 *
 * The concrete implementation of [InstantOutboxPublisher] supplied by the caller
 * will handle the event.
 */
class InstantOutboxEvent(
  val outbox: OutboxItem,
)
