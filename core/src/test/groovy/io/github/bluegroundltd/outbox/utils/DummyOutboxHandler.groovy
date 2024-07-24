package io.github.bluegroundltd.outbox.utils

import io.github.bluegroundltd.outbox.item.OutboxType

import java.time.Clock

class DummyOutboxHandler extends MockOutboxHandler {
  private final static OutboxType TYPE = new DummyOutboxType()

  DummyOutboxHandler(Clock clock = null) {
    super(TYPE, clock)
  }
}

class DelayingOutboxHandler extends DummyOutboxHandler {

  DelayingOutboxHandler(Clock clock = null) {
    super(clock)
  }

  @Override
  void handle(String payload) { sleep(50000) }
}
