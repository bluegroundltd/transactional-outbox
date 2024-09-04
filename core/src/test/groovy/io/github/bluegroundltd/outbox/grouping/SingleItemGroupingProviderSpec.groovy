package io.github.bluegroundltd.outbox.grouping

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxItemGroup
import spock.lang.Specification

class SingleItemGroupingProviderSpec extends Specification {
  private SingleItemGroupingProvider provider = new SingleItemGroupingProvider()

  def "Should generate separate groups for each item"() {
    given:
      def items = (1..5).collect { GroovyMock(OutboxItem) }

    and:
      def expectedGroups = items.collect { OutboxItemGroup.of(it) }

    when:
      def groups = provider.execute(items)

    then:
      0 * _

    and:
      groups == expectedGroups
  }
}
