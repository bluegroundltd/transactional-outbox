package io.github.bluegroundltd.outbox.grouping

interface OutboxGroupingConfiguration {
  val groupingProvider: OutboxGroupingProvider
}

data class CustomGroupingProviderConfiguration(
  override val groupingProvider: OutboxGroupingProvider
) : OutboxGroupingConfiguration

data class CustomOrderingProviderConfiguration(
  private val orderingProvider: OutboxOrderingProvider
) : OutboxGroupingConfiguration {
  override val groupingProvider: OutboxGroupingProvider = GroupIdGroupingProvider(orderingProvider)
}

object DefaultGroupingConfiguration : OutboxGroupingConfiguration {
  override val groupingProvider: OutboxGroupingProvider = GroupIdGroupingProvider()
}

object SingleItemGroupingConfiguration : OutboxGroupingConfiguration {
  override val groupingProvider: OutboxGroupingProvider = SingleItemGroupingProvider()
}
