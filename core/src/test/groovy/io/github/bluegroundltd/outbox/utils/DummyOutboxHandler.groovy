package io.github.bluegroundltd.outbox.utils

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType

import java.time.Clock
import java.time.Duration
import java.time.Instant

class DummyOutboxHandler implements OutboxHandler {
  private static OutboxType type = new DummyOutboxType()

  private Clock clock

  DummyOutboxHandler(Clock clock = null) {
    this.clock = clock ?: Clock.systemUTC()
  }

  @Override
  OutboxType getSupportedType() {
    return type
  }

  @Override
  String serialize(OutboxPayload payload) {
    return "dummyPayload"
  }

  @Override
  Instant getNextExecutionTime(long currentRetries) {
    return Instant.now(clock) + Duration.ofHours(1)
  }

  @Override
  boolean hasReachedMaxRetries(long retries) {
    return false
  }

  @Override
  void handle(String payload) {}

  @Override
  void handleFailure(String payload) {}

  @Override
  Duration getRetentionDuration() {
    return Duration.ofHours(1)
  }
}

class DelayingOutboxHandler extends DummyOutboxHandler {

  DelayingOutboxHandler(Clock clock = null) {
    super(clock)
  }

  @Override
  void handle(String payload) { sleep(50000) }
}
