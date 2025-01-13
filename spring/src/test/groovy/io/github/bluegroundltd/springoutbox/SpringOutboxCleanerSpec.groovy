package io.github.bluegroundltd.springoutbox

import io.github.bluegroundltd.springoutbox.utils.UnitTestSpecification

class SpringOutboxCleanerSpec extends UnitTestSpecification {

  TransactionalOutbox transactionalOutbox
  SpringOutboxCleaner springOutboxCleaner

  def setup() {
    transactionalOutbox = Mock()
    springOutboxCleaner = new SpringOutboxCleaner(transactionalOutbox)
  }

  def "should cleanup the transactional outbox when run is invoked"() {
    when:
      springOutboxCleaner.run()

    then:
      1 * transactionalOutbox.cleanup()
      0 * _
  }
}
