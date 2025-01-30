package io.github.bluegroundltd.springoutbox

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional

@AutoConfiguration
internal class OutboxConfiguration {

  @Bean
  @Conditional(SpringOutboxSchedulerCondition::class)
  @ConditionalOnMissingBean(OutboxScheduler::class)
  fun defaultOutboxScheduler(transactionalOutbox: TransactionalOutbox): OutboxScheduler {
    return SpringOutboxScheduler(transactionalOutbox)
  }

  @Bean
  @Conditional(SpringOutboxCleanerCondition::class)
  @ConditionalOnMissingBean(OutboxCleaner::class)
  fun defaultOutboxCleaner(transactionalOutbox: TransactionalOutbox): OutboxCleaner {
    return SpringOutboxCleaner(transactionalOutbox)
  }

  @Bean
  @ConditionalOnMissingBean(OutboxShutdown::class)
  fun defaultOutboxShutdown(transactionalOutbox: TransactionalOutbox): OutboxShutdown {
    return SpringOutboxShutdown(transactionalOutbox)
  }
}
