package io.github.bluegroundltd.outbox.grouping

import io.github.bluegroundltd.outbox.item.OutboxItem
import spock.lang.Specification

class FifoOrderingProviderSpec extends Specification {
  private FifoOrderingProvider provider = new FifoOrderingProvider()

  def "Should return the items in the same order they were provided"() {
    given:
      def items = (1..5).collect { GroovyMock(OutboxItem) }

    when:
      def result = provider.execute(items)

    then:
      0 * _

    and:
      result == items
  }
}
