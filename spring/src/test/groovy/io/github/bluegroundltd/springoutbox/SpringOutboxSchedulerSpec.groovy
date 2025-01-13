package io.github.bluegroundltd.springoutbox

import io.github.bluegroundltd.springoutbox.utils.UnitTestSpecification

class SpringOutboxSchedulerSpec extends UnitTestSpecification {

  TransactionalOutbox transactionalOutbox
  SpringOutboxScheduler springOutboxScheduler

  def setup() {
    transactionalOutbox = Mock()
    springOutboxScheduler = new SpringOutboxScheduler(transactionalOutbox)
  }

  def "should monitor the transactional outbox when run is invoked"() {
    when:
      springOutboxScheduler.run()

    then:
      1 * transactionalOutbox.monitor(null)
      0 * _
  }
}
