description = "Transactional Outbox"

plugins {
  kotlin("jvm")
  id("org.jetbrains.dokka") version "1.9.0"
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
