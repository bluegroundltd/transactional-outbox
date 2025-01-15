package io.github.bluegroundltd.springoutbox.utils

import io.github.bluegroundltd.outbox.item.OutboxType

import java.time.Clock

class DummyOutboxHandler extends MockOutboxHandler {
  private final static OutboxType TYPE = new DummyOutboxType()

  DummyOutboxHandler(Clock clock = null) {
    super(TYPE, clock)
  }
}

