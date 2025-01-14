package io.github.bluegroundltd.springoutbox.converter

import io.github.bluegroundltd.springoutbox.TestOutboxType
import io.github.bluegroundltd.springoutbox.utils.OutboxItemEntityBuilder
import io.github.bluegroundltd.springoutbox.utils.UnitTestSpecification

class OutboxItemEntityConverterSpec extends UnitTestSpecification {
  OutboxTypeConverter outboxTypeConverter
  OutboxItemEntityConverter converter

  def setup() {
    outboxTypeConverter = Mock()
    converter = new OutboxItemEntityConverter(outboxTypeConverter)
  }

  def "should convert TransactionalOutboxItemEntity to OutboxItem"() {
    given:
      def entity = OutboxItemEntityBuilder.make().build()
      def outboxType = TestOutboxType.TYPE1

    when:
      def result = converter.convert(entity)

    then:
      1 * outboxTypeConverter.convert(entity.type) >> outboxType
      0 * _

    and:
      with(result) {
        id == entity.id
        type == outboxType
        status == entity.status
        payload == entity.payload
        groupId == entity.groupId
        retries == entity.retries
        nextRun == entity.nextRun
        lastExecution == entity.lastExecution
        rerunAfter == entity.rerunAfter
        deleteAfter == entity.deleteAfter
      }
  }
}
