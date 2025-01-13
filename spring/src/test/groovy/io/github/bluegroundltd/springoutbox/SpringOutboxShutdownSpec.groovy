package io.github.bluegroundltd.springoutbox

import io.github.bluegroundltd.springoutbox.utils.UnitTestSpecification

class SpringOutboxShutdownSpec extends UnitTestSpecification {

  TransactionalOutbox transactionalOutbox
  SpringOutboxShutdown springOutboxShutdown

  def setup() {
    transactionalOutbox = Mock()
    springOutboxShutdown = new SpringOutboxShutdown(transactionalOutbox)
  }
  def "should shutdown the transactional outbox when shutdown is invoked"() {
    when:
      springOutboxShutdown.shutdown()

    then:
      1 * transactionalOutbox.shutdown()
      0 * _
  }
}
