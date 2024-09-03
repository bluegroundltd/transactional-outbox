package io.github.bluegroundltd.outbox.grouping

import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType
import spock.lang.Specification

class RandomGroupIdProviderSpec extends Specification {
  private RandomGroupIdProvider provider = new RandomGroupIdProvider()

  def "Should generate a random group id"() {
    given:
      def type = GroovyMock(OutboxType)
      def payload = Mock(OutboxPayload)

    when:
      def groupId = provider.execute(type, payload)

    then:
      0 * _

    and:
      groupId
  }
}
