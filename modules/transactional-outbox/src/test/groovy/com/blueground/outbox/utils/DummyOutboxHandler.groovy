package com.blueground.outbox.utils

import com.blueground.outbox.OutboxHandler
import com.blueground.outbox.item.OutboxPayload
import com.blueground.outbox.item.OutboxType
import org.jetbrains.annotations.NotNull

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
}
