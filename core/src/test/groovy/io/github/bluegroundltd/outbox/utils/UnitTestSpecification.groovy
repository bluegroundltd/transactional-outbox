package io.github.bluegroundltd.outbox.utils

import spock.lang.Specification

class UnitTestSpecification extends Specification implements SpecHelper {
  def setupSpec() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
  }
}
