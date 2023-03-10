package io.github.bluegroundltd.outbox.unit

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.OutboxLocksProvider
import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxImpl
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.item.factory.OutboxItemFactory
import io.github.bluegroundltd.outbox.store.OutboxStore
import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import io.github.bluegroundltd.outbox.utils.UnitTestSpecification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ExecutorService

class OutboxAddSpec extends UnitTestSpecification {
  private static final Duration DURATION_ONE_HOUR = Duration.ofHours(1)
  Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  Map<OutboxType, OutboxHandler> handlers = Mock()
  OutboxLocksProvider locksProvider = Mock()
  OutboxStore store = Mock()
  InstantOutboxPublisher instantOutboxPublisher = Mock()
  OutboxItemFactory outboxItemFactory = Mock()
  ExecutorService executor = Mock()
  Duration threadPoolTimeOut = Duration.ofMillis(5000)
  TransactionalOutbox transactionalOutbox

  def setup() {
    transactionalOutbox = new TransactionalOutboxImpl(
      clock,
      handlers,
      locksProvider,
      store,
      instantOutboxPublisher,
      outboxItemFactory,
      DURATION_ONE_HOUR,
      executor,
      threadPoolTimeOut
    )
  }

  def "Should delegate to outbox store when add is called"() {
    given:
      def payload = GroovyMock(OutboxPayload)
      def type = GroovyMock(OutboxType)

    and:
      def outboxItem = OutboxItemBuilder.make().build()

    when:
      transactionalOutbox.add(type, payload, false)

    then:
      1 * type.getType() >> "type"
      1 * outboxItemFactory.makeScheduledOutboxItem(type, payload) >> outboxItem
      1 * store.insert(outboxItem)
      0 * _
  }

  def "Should delegate to outbox store and publisher when add is called with the shouldPublishAfterInsertion flag"() {
    given:
      def payload = GroovyMock(OutboxPayload)
      def type = GroovyMock(OutboxType)

    and:
      def outboxItem = OutboxItemBuilder.make().build()
      def savedOutbox = OutboxItemBuilder.make().build()

    when:
      transactionalOutbox.add(type, payload, true)

    then:
      1 * type.getType() >> "type"
      1 * outboxItemFactory.makeInstantOutbox(type, payload) >> outboxItem
      1 * store.insert(outboxItem) >> savedOutbox
      1 * instantOutboxPublisher.publish({
        assert it.outbox == savedOutbox
      })
      0 * _
  }
}
