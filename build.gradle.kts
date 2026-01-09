description = "Transactional Outbox"

plugins {
  kotlin("jvm")
  id("org.jetbrains.dokka") version "1.9.0"
  id("com.vanniktech.maven.publish") version "0.35.0"
}

subprojects {
  apply(plugin = "com.vanniktech.maven.publish")

  signing {
    // This is required to allow using the signing key via the CI in ASCII armored format.
    // https://docs.gradle.org/current/userguide/signing_plugin.html#sec:in-memory-keys
    if (!project.gradle.startParameter.taskNames.any {
        it.contains("publishToMavenLocal") ||
            it.contains("publishMavenPublicationToMavenLocal")
      }) {
      val signingKey: String? by project
      val signingPassword: String? by project
      // Use in-memory keys if provided (for CI), otherwise fall back to default signing config (keyring file)
      if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
      }
    }
  }
}

repositories {
  mavenCentral()
}

tasks.dokkaHtmlMultiModule {
  moduleName.set("Transactional Outbox")

  doLast {    // Replace the generic "All modules" titles with "Transactional Outbox"
    val indexFile = file(outputDirectory.get().asFile.resolve("index.html"))
    if (indexFile.exists()) {
      val content = indexFile.readText()
      val modifiedContent = content.replace(
        Regex("All modules"),
        "Transactional Outbox"
      )
      indexFile.writeText(modifiedContent)
    }
  }
}
