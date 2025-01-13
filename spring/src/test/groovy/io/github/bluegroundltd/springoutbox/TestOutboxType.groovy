package io.github.bluegroundltd.springoutbox

import io.github.bluegroundltd.outbox.item.OutboxType

enum TestOutboxType implements OutboxType {
  TYPE1,
  TYPE2

  @Override
  String getType() {
    return this.toString()
  }
}
