package io.github.bluegroundltd.outbox.utils

import groovy.transform.EqualsAndHashCode
import io.github.bluegroundltd.outbox.item.OutboxType

@EqualsAndHashCode(includeFields=true)
class SimpleOutboxType implements OutboxType {
  private final String type

  SimpleOutboxType(String type) {
    this.type = type
  }

  @Override
  String getType() {
    return type
  }
}
