package io.github.bluegroundltd.outbox.utils

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType

import java.time.Duration
import java.time.Instant

class DummyOutboxHandler implements OutboxHandler {
  private static OutboxType type = new DummyOutboxType()

  @Override
  OutboxType getSupportedType() {
    return type
  }

  @Override
  String serialize(OutboxPayload payload) {
    return null
  }

  @Override
  Instant getNextExecutionTime(long currentRetries) {
    return null
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

  @Override
  Duration getRetentionDuration() {
    return null
  }
}

class DummyHandler extends DummyOutboxHandler {

  @Override
  String serialize(OutboxPayload payload) {
    return "dummyPayload"
  }

  @Override
  Instant getNextExecutionTime(long currentRetries) {
    return Instant.now()
  }

  @Override
  void handle(String payload) { sleep(50000); }
}
