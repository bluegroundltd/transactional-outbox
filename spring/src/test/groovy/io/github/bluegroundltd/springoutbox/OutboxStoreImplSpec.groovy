package io.github.bluegroundltd.springoutbox

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.store.OutboxFilter
import io.github.bluegroundltd.outbox.store.OutboxStoreInsertHints
import io.github.bluegroundltd.springoutbox.database.OutboxDao
import io.github.bluegroundltd.springoutbox.utils.OutboxItemBuilder
import io.github.bluegroundltd.springoutbox.utils.UnitTestSpecification

class OutboxStoreImplSpec extends UnitTestSpecification {
  private final OutboxDao outboxDao = Mock()
  private final OutboxStoreImpl outboxStore = new OutboxStoreImpl(
    outboxDao
  )

  def "Should retrieve eligible items (including ones with same group id) when 'fetch' is invoked"() {
    given:
      def filter = GroovyMock(OutboxFilter)

    and: "Some eligible items where the second and fourth have group ids"
      def eligibleItems = (1..5).collect {
        if (it % 2 == 0) {
          OutboxItemBuilder.make().withGroupId().build()
        } else {
          OutboxItemBuilder.make().withoutGroupId().build()
        }
      }

    and: "Some additional items with the same group ids as the second and fourth one"
      def itemsWithGroupIds = [
        eligibleItems[1],
        eligibleItems[3]
      ]
      def groupIds = itemsWithGroupIds.collect { it.groupId }
      def firstGroupItems = (1..generateInt(5)).collect {
        OutboxItemBuilder.make().withGroupId(groupIds[0]).build()
      }
      def secondGroupItems = (1..generateInt(5)).collect {
        OutboxItemBuilder.make().withGroupId(groupIds[1]).build()
      }
      def sameGroupItems = firstGroupItems + itemsWithGroupIds + secondGroupItems

    and: "A collection of all the distinct items in the same order as they were fetched"
      def distinctItems = eligibleItems + firstGroupItems + secondGroupItems

    when:
      def result = outboxStore.fetch(filter)

    then:
      1 * outboxDao.fetchByFilter(filter) >> eligibleItems
      1 * outboxDao.fetchNonCompletedByGroupIds(groupIds.toSet(), OutboxStatus.COMPLETED) >> sameGroupItems

    and:
      result == distinctItems
  }

  def "Should retrieve eligible items when 'fetch' is invoked"() {
    given:
      def filter = GroovyMock(OutboxFilter)

    and: "Some eligible items where none has a group id"
      def eligibleItems = (1..5).collect {
        OutboxItemBuilder.make().withoutGroupId().build()
      }

    when:
      def result = outboxStore.fetch(filter)

    then:
      1 * outboxDao.fetchByFilter(filter) >> eligibleItems
      0 * _

    and:
      result == eligibleItems
  }

  def "Should save the item and flush based on the supplied hints when 'insert' is invoked"() {
    given:
      def outboxItem = GroovyMock(OutboxItem)
      def hints = new OutboxStoreInsertHints(forInstantProcessing, instantOrderingEnabled)

    and:
      def savedItem = OutboxItemBuilder.make().build() // needed for log statement that accesses the id property

    when:
      def result = outboxStore.insert(outboxItem, hints)

    then:
      (shouldFlush ? 1 : 0) * outboxDao.saveAndFlush(outboxItem) >> savedItem
      (shouldFlush ? 0 : 1) * outboxDao.save(outboxItem) >> savedItem
      0 * _

    and:
      result == savedItem

    where:
      forInstantProcessing | instantOrderingEnabled | shouldFlush
      false                | false                  | false
      false                | true                   | false
      true                 | false                  | true
      true                 | true                   | false
  }

  def "Should save the item when 'insert' is invoked with no hints"() {
    given:
      def outboxItem = GroovyMock(OutboxItem)

    and:
      def savedItem = OutboxItemBuilder.make().build() // needed for log statement that accesses the id property

    when:
      def result = outboxStore.insert(outboxItem)

    then:
      1 * outboxDao.save(outboxItem) >> savedItem
      0 * _

    and:
      result == savedItem
  }

  def "Should update an existing item when 'update' is invoked"() {
    given:
      def outboxItem = OutboxItemBuilder.make().build()

    and:
      def itemBuilder = OutboxItemBuilder.make()
      def fetchedItem = itemBuilder.build()
      def updatedItem = itemBuilder
        .withStatus(outboxItem.status)
        .withRetries(outboxItem.retries)
        .withNextRun(outboxItem.nextRun)
        .withLastExecution(outboxItem.lastExecution)
        .withRerunAfter(outboxItem.rerunAfter)
        .withDeleteAfter(outboxItem.deleteAfter)
        .build()
      def savedItem = itemBuilder.build()

    when:
      def result = outboxStore.update(outboxItem)

    then:
      1 * outboxDao.findByIdAndLock(outboxItem.id) >> fetchedItem
      1 * outboxDao.save(updatedItem) >> savedItem
      0 * _

    and:
      result == savedItem
  }

  def "Should throw an exception when 'update' is invoked but an item with a matching id is not found"() {
    given:
      def outboxItem = OutboxItemBuilder.make().build()

    when:
      outboxStore.update(outboxItem)

    then:
      1 * outboxDao.findByIdAndLock(outboxItem.id) >> null
      0 * _

    and:
      def ex = thrown(IllegalArgumentException)
      ex.message == "Failed to update outbox item with id ${outboxItem.id} as it doesn't exist."
  }

  def "Should invoke [outboxDao#deleteAllByDeleteAfterLessThanEqual] when 'deleteCompletedItems' is invoked"() {
    given:
      def now = generateInstant()

    when:
      outboxStore.deleteCompletedItems(now)

    then:
      1 * outboxDao.deleteAllByDeleteAfterLessThanEqual(now)
      0 * _

    and:
      noExceptionThrown()
  }
}
