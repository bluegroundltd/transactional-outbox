package io.github.bluegroundltd.outbox.grouping

import io.github.bluegroundltd.outbox.item.OutboxItemGroup
import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

class GroupIdGroupingProviderSpec extends Specification {
  private final OutboxOrderingProvider orderingProvider = Mock()
  private final GroupIdGroupingProvider provider = new GroupIdGroupingProvider(orderingProvider)

  def "Should use a [FifoOrderingProvider] if one is not supplied"() {
    given:
      def providerWithDefaultOrdering = new GroupIdGroupingProvider()

    and:
      def groupId = "group"
      def items = (1..5).collect {
        OutboxItemBuilder.make().withGroupId(groupId).build()
      }

    and:
      def expectedResult = [new OutboxItemGroup(items)]

    when:
      def result = providerWithDefaultOrdering.execute(items)

    then:
      0 * _

    and:
      def usedOrderingProvider = providerWithDefaultOrdering.orderingProvider
      usedOrderingProvider instanceof FifoOrderingProvider

    and:
      result == expectedResult
  }

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
      def group1 = [items[1]]
      def group2 = [items[2], items[5]]
      def group3 = [items[0], items[3], items[4]]
      // The groups are created in the order of the first appearance of the group ID.
      def groupedItems = [
        group3,
        group1,
        group2
      ]
      def expectedGroups = groupedItems.collect { new OutboxItemGroup(it) }

    when:
      def groups = provider.execute(items)

    then:
      0 * _
      groupedItems.eachWithIndex { itemsToOrder, index ->
        1 * orderingProvider.execute(itemsToOrder) >> itemsToOrder
      }

    and:
      groups == expectedGroups
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
