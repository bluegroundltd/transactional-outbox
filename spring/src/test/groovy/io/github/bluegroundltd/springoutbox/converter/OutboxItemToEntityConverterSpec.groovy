package io.github.bluegroundltd.springoutbox.converter

import io.github.bluegroundltd.springoutbox.utils.OutboxItemBuilder
import io.github.bluegroundltd.springoutbox.utils.UnitTestSpecification

class OutboxItemToEntityConverterSpec extends UnitTestSpecification {

  OutboxItemToEntityConverter converter

  def setup() {
    converter = new OutboxItemToEntityConverter()
  }

  def "should convert OutboxItem to TransactionalOutboxItemEntity"() {
    given:
      def item = OutboxItemBuilder.make().build()

    when:
      def result = converter.convert(item)

    then:
      with(result) {
        id == item.id
        type == item.type.type
        status == item.status
        payload == item.payload
        groupId == item.groupId
        retries == item.retries
        nextRun == item.nextRun
        lastExecution == item.lastExecution
        rerunAfter == item.rerunAfter
        deleteAfter == item.deleteAfter
      }
  }
}
