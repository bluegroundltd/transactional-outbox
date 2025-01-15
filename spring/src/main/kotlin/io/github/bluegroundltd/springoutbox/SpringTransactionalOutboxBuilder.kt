package io.github.bluegroundltd.springoutbox

import io.github.bluegroundltd.outbox.BuildStep
import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxBuilder
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.store.OutboxStore
import io.github.bluegroundltd.springoutbox.postgreslock.PostgresLockDao
import io.github.bluegroundltd.springoutbox.postgreslock.PostgresOutboxLocksProvider
import java.time.Clock
import org.springframework.stereotype.Component

/**
 * Builder for [TransactionalOutbox].
 *
 * Example on Spring Boot:
 * ``` kotlin
 *   fun transactionalOutbox(
 *     private val springOutboxBuilder: SpringTransactionalOutboxBuilder,
 *   ): TransactionalOutbox {
 *     return springOutboxBuilder
 *       .withClock(clock)
 *       .withHandlers(outboxHandlers)
 *       .withMonitorLocksIdentifier(monitorLocksIdentifier)
 *       .withCleanupLocksIdentifier(cleanupLocksIdentifier)
 *       .withThreadPoolSize(threadPoolSize)
 *       .withThreadPriority(threadPriority)
 *       .withThreadPoolTimeOut(threadPoolTimeOut)
 *       .addProcessorDecorator(outboxItemProcessorDecorator)
 *       .withGroupIdProvider(outboxGroupIdProvider)
 *       .withGroupingConfiguration(outboxGroupingConfiguration)
 *       .build()
 *   }
 *   ```
 */

@Component
class SpringTransactionalOutboxBuilder(
  private val postgresLockDao: PostgresLockDao,
  private val outboxStore: OutboxStore,
  private val instantOutboxPublisher: InstantOutboxPublisher,
) : SpringOutboxClockStep,
  SpringOutboxHandlersStep,
  SpringMonitorLocksIdentifierStep,
  SpringCleanupLocksIdentifierStep {

  private lateinit var transactionalOutboxBuilder: TransactionalOutboxBuilder

  /**
   * Sets the clock for the outbox.
   */
  override fun withClock(clock: Clock): SpringOutboxHandlersStep {
    transactionalOutboxBuilder = TransactionalOutboxBuilder.make(clock) as TransactionalOutboxBuilder
    return this
  }

  /**
   * Sets the handlers for the outbox.
   */
  override fun withHandlers(handlers: Set<OutboxHandler>): SpringMonitorLocksIdentifierStep {
    transactionalOutboxBuilder.withHandlers(handlers)
    return this
  }

  /**
   * Sets the locks identifier for the outbox monitor runs.
   */
  override fun withMonitorLocksIdentifier(locksIdentifier: Long): SpringCleanupLocksIdentifierStep {
    val locksProvider = PostgresOutboxLocksProvider(postgresLockDao, locksIdentifier)
    transactionalOutboxBuilder.withMonitorLocksProvider(locksProvider)
    return this
  }

  /**
   * Sets the locks identifier for the outbox cleanup runs.
   */
  override fun withCleanupLocksIdentifier(locksIdentifier: Long): BuildStep {
    val locksProvider = PostgresOutboxLocksProvider(postgresLockDao, locksIdentifier)
    transactionalOutboxBuilder.withCleanupLocksProvider(locksProvider)
    transactionalOutboxBuilder.withStore(outboxStore)
    transactionalOutboxBuilder.withInstantOutboxPublisher(instantOutboxPublisher)
    return transactionalOutboxBuilder
  }
}

interface SpringOutboxClockStep {
  fun withClock(clock: Clock): SpringOutboxHandlersStep
}

interface SpringOutboxHandlersStep {
  fun withHandlers(handlers: Set<OutboxHandler>): SpringMonitorLocksIdentifierStep
}

interface SpringMonitorLocksIdentifierStep {
  fun withMonitorLocksIdentifier(locksIdentifier: Long): SpringCleanupLocksIdentifierStep
}

interface SpringCleanupLocksIdentifierStep {
  fun withCleanupLocksIdentifier(locksIdentifier: Long): BuildStep
}
