pluginManagement {
  resolutionStrategy {
    eachPlugin {
      val detektVersion: String by settings
      val kotlinVersion: String by settings

      when {
        requested.id.namespace?.startsWith("org.jetbrains.kotlin") == true -> useVersion(kotlinVersion)
        requested.id.namespace?.startsWith("org.jetbrains.kotlin.plugin.noarg") == true -> useVersion(kotlinVersion)
        requested.id.id == "io.gitlab.arturbosch.detekt" -> useVersion(detektVersion)
      }
    }
  }
}

rootProject.name = "transactional-outbox"
include("core")
include("spring")
