package com.blueground.outbox

import com.blueground.outbox.item.OutboxItem
import com.blueground.outbox.item.OutboxPayload
import com.blueground.outbox.item.OutboxStatus
import com.blueground.outbox.item.OutboxType
import kotlin.NotImplementedError
import spock.lang.Specification

import java.time.Instant

class TransactionalOutboxImplSpec extends Specification {
  OutboxHandler handler = Mock()
  OutboxPersistor persistor = Mock()
  TransactionalOutbox transactionalOutbox

  def setup() {
    transactionalOutbox = new TransactionalOutboxImpl([handler], persistor)
  }

  def "Should throw UnsupportedOperationException when item isn't supported"() {
    given:
      def payload = GroovyMock(OutboxPayload)
      def type = GroovyMock(OutboxType)

    when:
      transactionalOutbox.add(type, payload)

    then:
      1 * type.getType() >> "type"
      1 * handler.supports(type) >> false
      0 * _

    and:
      thrown(UnsupportedOperationException)
  }

  def "Should delegate to outbox persistor when add is called"() {
    given:
      def payload = GroovyMock(OutboxPayload)
      def type = GroovyMock(OutboxType)

    and:
      def serializedPayload = "serializedPayload"
      def nextRun = GroovyMock(Instant)

    and:
      def expectedOutboxItem = new OutboxItem(
        null,
        type,
        OutboxStatus.PENDING,
        serializedPayload,
        0,
        nextRun,
        null,
        null
      )

    when:
      transactionalOutbox.add(type, payload)

    then:
      1 * handler.supports(type) >> true
      1 * handler.serialize(payload) >> serializedPayload
      1 * handler.getNextExecutionTime(0) >> nextRun
      1 * persistor.insert(_) >> { OutboxItem item ->
        with (item) {
          id == expectedOutboxItem.id
          type == expectedOutboxItem.type
          status == expectedOutboxItem.status
          item.payload == expectedOutboxItem.payload
          retries == expectedOutboxItem.retries
          nextRun == expectedOutboxItem.nextRun
          lastExecution == expectedOutboxItem.lastExecution
          rerunAfter == expectedOutboxItem.rerunAfter
        }
      }
      0 * _
  }

  def "Should throw a NotImplementedError when monitor is called"() {
    when:
      transactionalOutbox.monitor()

    then:
      thrown(NotImplementedError)
  }
}
