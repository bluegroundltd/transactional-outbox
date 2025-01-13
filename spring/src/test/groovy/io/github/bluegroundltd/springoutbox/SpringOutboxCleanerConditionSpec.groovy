package io.github.bluegroundltd.springoutbox

import io.github.bluegroundltd.springoutbox.utils.UnitTestSpecification
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.env.Environment
import org.springframework.core.type.AnnotatedTypeMetadata

class SpringOutboxCleanerConditionSpec extends UnitTestSpecification {

  SpringOutboxCleanerCondition springOutboxCleanerCondition

  def setup() {
    springOutboxCleanerCondition = new SpringOutboxCleanerCondition()
  }

  def "return property from context environment"() {
    given:
      ConditionContext context = Mock()
      AnnotatedTypeMetadata metadata = Mock()
      Environment environment = Mock()

    when:
      def result = springOutboxCleanerCondition.matches(context, metadata)

    then:
      1 * context.getEnvironment() >> environment
      1 * environment.getProperty("jobs.transactionalOutbox.cleaner.enabled") >> envProperty
      0 * _

    and:
      result == expected

    where:
      envProperty | expected
      "true"      | true
      "false"     | false
      null        | false
  }
}
