package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.executor.FixedThreadPoolExecutorServiceFactory
import io.github.bluegroundltd.outbox.grouping.DefaultGroupingConfiguration
import io.github.bluegroundltd.outbox.grouping.OutboxGroupingConfiguration
import io.github.bluegroundltd.outbox.grouping.OutboxGroupIdProvider
import io.github.bluegroundltd.outbox.grouping.NullGroupIdProvider
import io.github.bluegroundltd.outbox.grouping.SingleItemGroupingConfiguration
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.item.factory.OutboxItemFactory
import io.github.bluegroundltd.outbox.processing.OutboxItemProcessorDecorator
import io.github.bluegroundltd.outbox.processing.OutboxProcessingHostComposer
import io.github.bluegroundltd.outbox.store.OutboxStore
import java.time.Clock
import java.time.Duration

/**
 * Builder for [TransactionalOutbox].
 *
 * Example on Spring Boot:
 * ``` kotlin
 *   fun transactionalOutbox(): TransactionalOutbox {
 *     return TransactionalOutboxBuilder
 *       .make(clock)
 *       .withHandlers(outboxHandlers)
 *       .withMonitorLocksProvider(monitorLocksProvider)
 *       .withCleanupLocksProvider(cleanupLocksProvider)
 *       .withStore(outboxStore)
 *       .withInstantOutboxPublisher(instantOutboxPublisher)
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
class TransactionalOutboxBuilder(
  private val clock: Clock,
  private val rerunAfterDuration: Duration = DEFAULT_RERUN_AFTER_DURATION
) : OutboxHandlersStep,
  MonitorLocksProviderStep,
  CleanupLocksProviderStep,
  StoreStep,
  InstantOutboxPublisherStep,
  BuildStep {
  private val handlers: MutableMap<OutboxType, OutboxHandler> = mutableMapOf()
  private var threadPoolSize: Int? = null
  private var threadPriority: Int? = null
  private var threadPoolTimeOut: Duration = DEFAULT_THREAD_POOL_TIMEOUT
  private var decorators: MutableList<OutboxItemProcessorDecorator> = mutableListOf()
  private var instantOrderingEnabled: Boolean = false
  private lateinit var monitorLocksProvider: OutboxLocksProvider
  private lateinit var cleanupLocksProvider: OutboxLocksProvider
  private lateinit var store: OutboxStore
  private lateinit var instantOutboxPublisher: InstantOutboxPublisher
  private var groupIdProvider: OutboxGroupIdProvider = NullGroupIdProvider()
  private var groupingConfiguration: OutboxGroupingConfiguration = DefaultGroupingConfiguration

  companion object {
    private val DEFAULT_RERUN_AFTER_DURATION: Duration = Duration.ofHours(1)
    private val DEFAULT_THREAD_POOL_TIMEOUT: Duration = Duration.ofSeconds(5)

    /**
     * Creates a new [OutboxHandlersStep] for the builder.
     */
    @JvmStatic
    fun make(clock: Clock): OutboxHandlersStep {
      return TransactionalOutboxBuilder(clock)
    }
  }

  /**
   * Sets the handlers for the outbox.
   */
  override fun withHandlers(handlers: Set<OutboxHandler>): MonitorLocksProviderStep {
    validateNoDuplicateHandlerSupportedTypes(handlers)
    handlers.associateByTo(this.handlers) { it.getSupportedType() }
    return this
  }

  private fun validateNoDuplicateHandlerSupportedTypes(handlers: Set<OutboxHandler>) {
    val typesWithMoreThanOneHandlers = handlers
      .groupBy { it.getSupportedType() }
      .filter { it.value.size > 1 }

    if (typesWithMoreThanOneHandlers.isNotEmpty()) {
      val typesWithDuplicateHandlers = concatenateTypesWithDuplicateHandlers(typesWithMoreThanOneHandlers)
      throw IllegalArgumentException("More than one handlers provided for types: $typesWithDuplicateHandlers")
    }
  }

  private fun concatenateTypesWithDuplicateHandlers(
    typesWithMoreThanOneHandlers: Map<OutboxType, List<OutboxHandler>>
  ): String {
    val typesWithMoreThanOneHandlersFlattened = typesWithMoreThanOneHandlers.entries.joinToString(
      separator = ", ",
      transform = {
        // Transforms entries to "type1 -> [handlerA, handlerB]"
        it.key.getType() +
          " -> [" +
          it.value.joinToString { handler -> handler.javaClass.simpleName } +
          "]"
      }
    )
    return typesWithMoreThanOneHandlersFlattened
  }

  /**
   * Sets the locks provider for the outbox monitor runs.
   */
  override fun withMonitorLocksProvider(locksProvider: OutboxLocksProvider): CleanupLocksProviderStep {
    this.monitorLocksProvider = locksProvider
    return this
  }

  /**
   * Sets the locks provider for the outbox cleanup runs.
   */
  override fun withCleanupLocksProvider(locksProvider: OutboxLocksProvider): StoreStep {
    this.cleanupLocksProvider = locksProvider
    return this
  }

  /**
   * Sets the store for the outbox.
   */
  override fun withStore(store: OutboxStore): InstantOutboxPublisherStep {
    this.store = store
    return this
  }

  /**
   * Sets the publisher for instant outboxes.
   */
  override fun withInstantOutboxPublisher(instantOutboxPublisher: InstantOutboxPublisher): BuildStep {
    this.instantOutboxPublisher = instantOutboxPublisher
    return this
  }

  /**
   * Sets the thread pool size for the outbox.
   */
  override fun withThreadPoolSize(threadPoolSize: Int): BuildStep {
    this.threadPoolSize = threadPoolSize
    return this
  }

  /**
   * Sets the priority for the threads in the thread pool.
   */
  override fun withThreadPriority(threadPriority: Int): BuildStep {
    this.threadPriority = threadPriority
    return this
  }

  /**
   * Sets the thread pool timeout upon shutdown for the outbox.
   */
  override fun withThreadPoolTimeOut(threadPoolTimeOut: Duration): BuildStep {
    this.threadPoolTimeOut = threadPoolTimeOut
    return this
  }

  /**
   * Adds a decorator to be applied before asynchronously invoking Outbox Item Processors which,
   * in turn, invokes the corresponding [OutboxHandler].
   *
   * Decorators are applied in the order they have been added, so a decorator will wrap the one added before it.
   * Decorators are optional; not adding any just means that the Processors will be invoked directly.
   */
  override fun addProcessorDecorator(decorator: OutboxItemProcessorDecorator): BuildStep {
    this.decorators.add(decorator)
    return this
  }

  /**
   * Flag which indicates whether new instant processing mechanism should be used or not.
   *
   * The new mechanism uses monitor to fetch the item along with its group sibling items and support ordering.
   * The old mechanism processes the instant outbox item directly without fetching it from store.
   *
   * - true:  Use the new mechanism for instant processing and allow ordering of grouped items.
   * - false: Use the old mechanism for instant processing.
   *
   * If not explicitly set, the value defaults to `false`. This minimizes unexpected disruption to applications
   * already using the transactional outbox. However, in the coming versions this will change and the value
   * will default to `true`. So, any applications that wish to have this feature disabled, should explicitly set it.
   */
  override fun withInstantOrderingEnabled(instantOrderingEnabled: Boolean): BuildStep {
    this.instantOrderingEnabled = instantOrderingEnabled
    return this
  }

  /**
   * Sets the group id provider for the outbox that will be used to set corresponding field when an item is added.
   *
   * If not set, a default [OutboxGroupIdProvider] is used that always returns null, effectively indicating that
   * there are no groups.
   */
  override fun withGroupIdProvider(groupIdProvider: OutboxGroupIdProvider): BuildStep {
    this.groupIdProvider = groupIdProvider
    return this
  }

  /**
   * Sets the grouping configuration for the outbox.
   *
   * If not set, a default [OutboxGroupingConfiguration] is used which provides grouping based on the `groupId`
   * field and FIFO ordering.
   */
  override fun withGroupingConfiguration(outboxGroupingConfiguration: OutboxGroupingConfiguration): BuildStep {
    this.groupingConfiguration = outboxGroupingConfiguration
    return this
  }

  /**
   * Builds the outbox.
   */
  override fun build(): TransactionalOutbox {
    val executorServiceFactory = FixedThreadPoolExecutorServiceFactory(threadPoolSize, threadPriority)
    val outboxItemFactory = OutboxItemFactory(clock, handlers.toMap(), rerunAfterDuration, groupIdProvider)

    return TransactionalOutboxImpl(
      clock,
      handlers.toMap(),
      monitorLocksProvider,
      cleanupLocksProvider,
      store,
      instantOutboxPublisher,
      outboxItemFactory,
      rerunAfterDuration,
      executorServiceFactory.make(),
      decorators,
      threadPoolTimeOut,
      OutboxProcessingHostComposer(),
      instantOrderingEnabled,
      groupingConfiguration.groupingProvider
    )
  }
}

interface OutboxHandlersStep {
  fun withHandlers(handlers: Set<OutboxHandler>): MonitorLocksProviderStep
}

interface MonitorLocksProviderStep {
  fun withMonitorLocksProvider(locksProvider: OutboxLocksProvider): CleanupLocksProviderStep
}

interface CleanupLocksProviderStep {
  fun withCleanupLocksProvider(locksProvider: OutboxLocksProvider): StoreStep
}

interface StoreStep {
  fun withStore(store: OutboxStore): InstantOutboxPublisherStep
}

interface InstantOutboxPublisherStep {
  fun withInstantOutboxPublisher(instantOutboxPublisher: InstantOutboxPublisher): BuildStep
}

interface BuildStep {
  fun withThreadPoolSize(threadPoolSize: Int): BuildStep
  fun withThreadPriority(threadPriority: Int): BuildStep
  fun withThreadPoolTimeOut(threadPoolTimeOut: Duration): BuildStep
  fun withInstantOrderingEnabled(instantOrderingEnabled: Boolean): BuildStep
  fun addProcessorDecorator(decorator: OutboxItemProcessorDecorator): BuildStep

  fun withGroupIdProvider(groupIdProvider: OutboxGroupIdProvider): BuildStep
  fun withGroupingConfiguration(outboxGroupingConfiguration: OutboxGroupingConfiguration): BuildStep
  fun withGrouping() = withGroupingConfiguration(DefaultGroupingConfiguration)
  fun withoutGrouping() = withGroupingConfiguration(SingleItemGroupingConfiguration)

  fun build(): TransactionalOutbox
}
