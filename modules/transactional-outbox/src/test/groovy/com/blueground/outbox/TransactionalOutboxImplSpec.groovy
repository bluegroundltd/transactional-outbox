package com.blueground.outbox

import kotlin.NotImplementedError
import spock.lang.Specification

class TransactionalOutboxImplSpec extends Specification {
  TransactionalOutbox transactionalOutbox

  def setup() {
    transactionalOutbox = new TransactionalOutboxImpl()
  }

  def "Should throw a NotImplementedError when add is called"() {
    when:
      transactionalOutbox.add()

    then:
      thrown(NotImplementedError)
  }

  def "Should throw a NotImplementedError when monitor is called"() {
    when:
      transactionalOutbox.monitor()

    then:
      thrown(NotImplementedError)
  }
}
