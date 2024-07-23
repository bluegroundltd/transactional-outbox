package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.executor.FixedThreadPoolExecutorServiceFactory
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.item.factory.OutboxItemFactory
import io.github.bluegroundltd.outbox.store.OutboxStore
import java.time.Clock
import java.time.Duration
import kotlin.properties.Delegates

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
  private var threadPoolSize by Delegates.notNull<Int>()
  private var threadPoolTimeOut: Duration = DEFAULT_THREAD_POOL_TIMEOUT
  private var decorators: MutableList<OutboxItemProcessorDecorator> = mutableListOf()
  private lateinit var monitorLocksProvider: OutboxLocksProvider
  private lateinit var cleanupLocksProvider: OutboxLocksProvider
  private lateinit var store: OutboxStore
  private lateinit var instantOutboxPublisher: InstantOutboxPublisher

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
   * Builds the outbox.
   */
  override fun build(): TransactionalOutbox {
    val executorServiceFactory = FixedThreadPoolExecutorServiceFactory()
    val outboxItemFactory = OutboxItemFactory(clock, handlers.toMap(), rerunAfterDuration)

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
        OutboxProcessingHostBuilder()
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
  fun withThreadPoolTimeOut(threadPoolTimeOut: Duration): BuildStep
  fun addProcessorDecorator(decorator: OutboxItemProcessorDecorator): BuildStep
  fun build(): TransactionalOutbox
}
