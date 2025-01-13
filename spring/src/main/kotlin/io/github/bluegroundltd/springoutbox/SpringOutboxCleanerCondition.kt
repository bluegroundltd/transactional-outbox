package io.github.bluegroundltd.springoutbox

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

class SpringOutboxCleanerCondition : Condition {
  override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
    return context.environment.getProperty("jobs.transactionalOutbox.cleaner.enabled")?.toBoolean() ?: false
  }
}
