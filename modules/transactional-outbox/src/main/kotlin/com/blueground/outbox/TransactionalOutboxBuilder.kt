package com.blueground.outbox

import com.blueground.outbox.executor.FixedThreadPoolExecutorServiceFactory
import com.blueground.outbox.item.OutboxType
import com.blueground.outbox.store.OutboxStore
import java.lang.IllegalArgumentException
import java.time.Clock
import java.time.Duration
import kotlin.properties.Delegates

class TransactionalOutboxBuilder(
  private val clock: Clock,
  private val rerunAfterDuration: Duration = DEFAULT_RERUN_AFTER_DURATION
) : OutboxHandlersStep, LocksProviderStep, StoreStep, BuildStep {
  val handlers: MutableMap<OutboxType, OutboxHandler> = mutableMapOf()
  private var locksIdentifier by Delegates.notNull<Long>()
  private lateinit var locksProvider: OutboxLocksProvider
  private lateinit var store: OutboxStore

  companion object {
    private val DEFAULT_RERUN_AFTER_DURATION: Duration = Duration.ofHours(1)

    @JvmStatic
    fun make(clock: Clock): OutboxHandlersStep {
      return TransactionalOutboxBuilder(clock)
    }
  }

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

  override fun withLocksProvider(locksIdentifier: Long, locksProvider: OutboxLocksProvider): StoreStep {
    this.locksIdentifier = locksIdentifier
    this.locksProvider = locksProvider
    return this
  }

  override fun withStore(store: OutboxStore): BuildStep {
    this.store = store
    return this
  }

  override fun build(): TransactionalOutbox {
    val executorServiceFactory = FixedThreadPoolExecutorServiceFactory()

    return TransactionalOutboxImpl(
      clock,
      handlers.toMap(),
      locksIdentifier,
      locksProvider,
      store,
      rerunAfterDuration,
      executorServiceFactory.make()
    )
  }
}

interface OutboxHandlersStep {
  fun withHandlers(handlers: Set<OutboxHandler>): LocksProviderStep
}

interface LocksProviderStep {
  fun withLocksProvider(locksIdentifier: Long, locksProvider: OutboxLocksProvider): StoreStep
}

interface StoreStep {
  fun withStore(store: OutboxStore): BuildStep
}

interface BuildStep {
  fun build(): TransactionalOutbox
}
