package io.github.bluegroundltd.outbox.utils

import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode(includeFields=true)
class DummyOutboxType extends SimpleOutboxType {
  DummyOutboxType() {
    super("dummyOutboxType")
  }
}
