package io.github.bluegroundltd.springoutbox.database

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.store.OutboxFilter
import io.github.bluegroundltd.springoutbox.utils.KotlinUtils
import io.github.bluegroundltd.springoutbox.utils.OutboxItemBuilder
import io.github.bluegroundltd.springoutbox.utils.OutboxItemEntityBuilder
import io.github.bluegroundltd.springoutbox.utils.UnitTestSpecification
import org.springframework.core.convert.ConversionService

class OutboxDaoImplSpec extends UnitTestSpecification implements KotlinUtils {
  private final OutboxRepository transactionalOutboxRepository = Mock()
  private final ConversionService conversionService = Mock()
  private final OutboxDaoImpl outboxDao = new OutboxDaoImpl(
    transactionalOutboxRepository,
    conversionService
  )

  def "Should find an entity by id when 'findByIdAndLock' is invoked and convert to item"() {
    given:
      def outboxItemEntity = OutboxItemEntityBuilder.make().build()
      def outboxItem = OutboxItemBuilder.make().build()

    when:
      def result = outboxDao.findByIdAndLock(outboxItem.id)

    then:
      1 * transactionalOutboxRepository.findById(outboxItem.id) >> Optional.of(outboxItemEntity)
      1 * conversionService.convert(outboxItemEntity, OutboxItem.class) >> outboxItem
      0 * _

    and:
      result == outboxItem
  }

  def "Should fetch all entities and convert to items when 'fetch' is invoked, using the outboxFilter"() {
    given:
      def outboxFilter = new OutboxFilter(generateInstant(), generateInstant(), generateLong())

    and:
      def outboxItemEntities = (1..5).collect {
        OutboxItemEntityBuilder.make().build()
      }
      def outboxItems = outboxItemEntities.collect {
        OutboxItemBuilder.make().build()
      }

    when:
      def result = outboxDao.fetchByFilter(outboxFilter)

    then:
      1 * transactionalOutboxRepository.findByFilter(
        outboxFilter.outboxPendingFilter.status.name(),
        outboxFilter.outboxPendingFilter.nextRunLessThan,
        outboxFilter.outboxRunningFilter.status.name(),
        outboxFilter.outboxRunningFilter.rerunAfterLessThan
      ) >> outboxItemEntities
      outboxItemEntities.eachWithIndex { OutboxItemEntity entry, int i ->
        1 * conversionService.convert(entry, OutboxItem.class) >> outboxItems[i]
      }
      0 * _

    and:
      result == outboxItems
  }

  def "Should fetch entities based on parameters and convert to items when 'fetchNonCompletedByGroupIds' is invoked"() {
    given:
      def groupIds = (1..5).collect { generateString() }
      def outboxStatus = OutboxStatus.COMPLETED

    and:
      def outboxItemEntities = (1..10).collect {
        OutboxItemEntityBuilder.make().build()
      }
      def outboxItems = outboxItemEntities.collect {
        OutboxItemBuilder.make().build()
      }

    when:
      def result = outboxDao.fetchNonCompletedByGroupIds(groupIds, outboxStatus)

    then:
      1 * transactionalOutboxRepository.findAllByGroupIdInAndStatusNot(groupIds.toSet(), outboxStatus) >> outboxItemEntities
      outboxItemEntities.eachWithIndex { OutboxItemEntity entry, int i ->
        1 * conversionService.convert(entry, OutboxItem.class) >> outboxItems[i]
      }
      0 * _

    and:
      result == outboxItems
  }

  def "Should call the repo to delete entities after less than timestamp when 'deleteAllByDeleteAfterLessThanEqual' is called"() {
    given:
      def timestamp = generateInstant()

    when:
      outboxDao.deleteAllByDeleteAfterLessThanEqual(timestamp)

    then:
      1 * transactionalOutboxRepository.deleteAllByDeleteAfterLessThanEqual(timestamp)
      0 * _
  }

  def "Should save the entity when 'save' is invoked"() {
    given:
      def outboxItem = OutboxItemBuilder.make().withId(id).build()
      def outboxItemEntity = OutboxItemEntityBuilder.make().build()
      def savedOutboxItemEntity = OutboxItemEntityBuilder.make().build()
      def savedOutboxItem = OutboxItemBuilder.make().build()

    when:
      def result = outboxDao.save(outboxItem)

    then:
      findByIdCalled * transactionalOutboxRepository.findById(outboxItem.id) >> Optional.of(outboxItemEntity)
      converterCalled * conversionService.convert(outboxItem, OutboxItemEntity.class) >> outboxItemEntity
      1 * transactionalOutboxRepository.save(outboxItemEntity) >> savedOutboxItemEntity
      1 * conversionService.convert(savedOutboxItemEntity, OutboxItem.class) >> savedOutboxItem
      0 * _

    and:
      result == savedOutboxItem

    where:
      id             | findByIdCalled | converterCalled
      generateLong() | 1              | 0
      null           | 0              | 1
  }

  def "Should save the entity and flush when 'saveAndFlush' is invoked"() {
    given:
      def outboxItem = OutboxItemBuilder.make().withId(id).build()
      def outboxItemEntity = OutboxItemEntityBuilder.make().build()
      def savedOutboxItemEntity = OutboxItemEntityBuilder.make().build()
      def savedOutboxItem = OutboxItemBuilder.make().build()

    when:
      def result = outboxDao.saveAndFlush(outboxItem)

    then:
      findByIdCalled * transactionalOutboxRepository.findById(outboxItem.id) >> Optional.of(outboxItemEntity)
      converterCalled * conversionService.convert(outboxItem, OutboxItemEntity.class) >> outboxItemEntity
      1 * transactionalOutboxRepository.saveAndFlush(outboxItemEntity) >> savedOutboxItemEntity
      1 * conversionService.convert(savedOutboxItemEntity, OutboxItem.class) >> savedOutboxItem
      0 * _

    and:
      result == savedOutboxItem

    where:
      id             | findByIdCalled | converterCalled
      generateLong() | 1              | 0
      null           | 0              | 1
  }
}
