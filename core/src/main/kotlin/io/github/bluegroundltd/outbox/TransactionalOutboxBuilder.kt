package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.executor.FixedThreadPoolExecutorServiceFactory
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.store.OutboxStore
import java.time.Clock
import java.time.Duration
import kotlin.properties.Delegates

/**
 * Builder for [TransactionalOutbox].
 *
 * Example on Spring Boot:
 * ``` kotlin
 *   @Bean
 *   fun transactionalOutbox(): TransactionalOutbox {
 *     val locksProvider = OutboxLocksProvider(postgresLockDao, id)
 *
 *     logger.info(
 *       "Initializing outbox with handler(s) [${outboxHandlers.joinToString { it.javaClass.simpleName }}], " +
 *         "locks provider \"${locksProvider}\" and store \"${outboxStore.javaClass.simpleName}\""
 *     )
 *
 *     return TransactionalOutboxBuilder
 *       .make(clock)
 *       .withHandlers(outboxHandlers)
 *       .withLocksProvider(locksProvider)
 *       .withStore(outboxStore)
 *       .build()
 *   }
 *   ```
 */
class TransactionalOutboxBuilder(
    private val clock: Clock,
    private val rerunAfterDuration: Duration = DEFAULT_RERUN_AFTER_DURATION
) : OutboxHandlersStep, LocksProviderStep, StoreStep, BuildStep {
  val handlers: MutableMap<OutboxType, OutboxHandler> = mutableMapOf()
  private var threadPoolSize by Delegates.notNull<Int>()
  private var threadPoolTimeOut: Duration = DEFAULT_THREAD_POOL_TIMEOUT
  private var decorator: OutboxItemProcessorDecorator? = null
  private lateinit var locksProvider: OutboxLocksProvider
  private lateinit var store: OutboxStore

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
  override fun withHandlers(handlers: Set<OutboxHandler>): LocksProviderStep {
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
   * Sets the locks provider for the outbox.
   */
  override fun withLocksProvider(locksProvider: OutboxLocksProvider): StoreStep {
    this.locksProvider = locksProvider
    return this
  }

  /**
   * Sets the store for the outbox.
   */
  override fun withStore(store: OutboxStore): BuildStep {
    this.store = store
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
   * Sets the decorator to be applied before asynchronously invoking the Outbox Item Processor which,
   * in turn, invokes the corresponding {@link OutboxItemHandler}. If not set or set to `null`,
   * no decoration will be applied.
   */
  override fun withProcessorDecorator(decorator: OutboxItemProcessorDecorator): BuildStep {
    this.decorator = decorator
    return this
  }

  /**
   * Builds the outbox.
   */
  override fun build(): TransactionalOutbox {
    val executorServiceFactory = FixedThreadPoolExecutorServiceFactory()

    return TransactionalOutboxImpl(
        clock,
        handlers.toMap(),
        locksProvider,
        store,
        rerunAfterDuration,
        executorServiceFactory.make(),
        decorator,
        threadPoolTimeOut
    )
  }
}

interface OutboxHandlersStep {
  fun withHandlers(handlers: Set<OutboxHandler>): LocksProviderStep
}

interface LocksProviderStep {
  fun withLocksProvider(locksProvider: OutboxLocksProvider): StoreStep
}

interface StoreStep {
  fun withStore(store: OutboxStore): BuildStep
}

interface BuildStep {
  fun withThreadPoolSize(threadPoolSize: Int): BuildStep
  fun withThreadPoolTimeOut(threadPoolTimeOut: Duration): BuildStep
  fun withProcessorDecorator(decorator: OutboxItemProcessorDecorator): BuildStep
  fun build(): TransactionalOutbox
}
