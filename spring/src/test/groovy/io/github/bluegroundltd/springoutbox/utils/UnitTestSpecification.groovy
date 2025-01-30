package io.github.bluegroundltd.springoutbox.utils

import spock.lang.Specification

/**
 * Super class specification of unit tests
 */
class UnitTestSpecification extends Specification implements SpecHelper {

  def setupSpec() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
  }

}
