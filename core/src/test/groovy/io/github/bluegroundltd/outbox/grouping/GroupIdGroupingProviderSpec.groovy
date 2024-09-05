package io.github.bluegroundltd.outbox.grouping

import io.github.bluegroundltd.outbox.item.OutboxItemGroup
import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

class GroupIdGroupingProviderSpec extends Specification {
  private GroupIdGroupingProvider provider = new GroupIdGroupingProvider()

  def "Should generate groups based on the group ID of each item"() {
    given:
      def items = [
        OutboxItemBuilder.make().withGroupId("group3").build(),
        OutboxItemBuilder.make().withGroupId("group1").build(),
        OutboxItemBuilder.make().withGroupId("group2").build(),
        OutboxItemBuilder.make().withGroupId("group3").build(),
        OutboxItemBuilder.make().withGroupId("group3").build(),
        OutboxItemBuilder.make().withGroupId("group2").build()
      ]

    and:
      def group1 = new OutboxItemGroup([items[1]])
      def group2 = new OutboxItemGroup([items[2], items[5]])
      def group3 = new OutboxItemGroup([items[0], items[3], items[4]])

    when:
      def groups = provider.execute(items)

    then:
      0 * _

    and:
      // The groups are created in the order of the first appearance of the group ID.
      groups == [
        group3,
        group1,
        group2,
      ]
  }

  def "Should return an empty list when there are no items"() {
    when:
      def groups = provider.execute([])

    then:
      0 * _

    and:
      groups == []
  }

  def "Should throw an exception when the group ID is null"() {
    given:
      def items = (1..3).collect { OutboxItemBuilder.make().build() } +
        OutboxItemBuilder.make().withoutGroupId().build()

    when:
      provider.execute(items)

    then:
      0 * _

    and:
      def ex = thrown(NullPointerException)
      ex
  }
}
