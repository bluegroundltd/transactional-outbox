description = "Transactional Outbox :: spring"

plugins {
  // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
  kotlin("jvm")
  java
  groovy
  codenarc
  jacoco
  id("io.gitlab.arturbosch.detekt")
  id("org.jetbrains.dokka") version "1.9.0"
  id("org.jetbrains.kotlin.plugin.allopen") version "2.1.0"
  id("org.jetbrains.kotlin.plugin.jpa") version "2.1.0"
  id("com.vanniktech.maven.publish") version "0.25.3"
}

repositories {
  // Use Maven Central for resolving dependencies.
  mavenCentral()
}

val detektVersion: String by project
val kotlinVersion: String by project
val slf4jVersion: String by project
val spockVersion: String by project
val spockReportsVersion: String by project
val springBootVersion: String by project
val springFrameworkVersion: String by project
val springDataJpaVersion: String by project
val jakartaPersistenceApi: String by project

dependencies {
  api("io.github.bluegroundltd:transactional-outbox-core:2.3.2")

  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.springframework.boot:spring-boot-autoconfigure:${springBootVersion}")
  implementation("org.springframework:spring-context:${springFrameworkVersion}")
  implementation("org.springframework:spring-tx:${springFrameworkVersion}")
  implementation("org.springframework.data:spring-data-jpa:${springDataJpaVersion}")
  implementation("jakarta.persistence:jakarta.persistence-api:${jakartaPersistenceApi}")
  implementation("org.slf4j:slf4j-api:${slf4jVersion}")

  testImplementation("org.spockframework:spock-core:$spockVersion")
  testImplementation("com.athaydes:spock-reports:$spockReportsVersion")
  testRuntimeOnly("org.objenesis:objenesis:3.3")
  testRuntimeOnly("net.bytebuddy:byte-buddy:1.12.19")
  testRuntimeOnly("org.slf4j:slf4j-simple:${slf4jVersion}")

  detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:$detektVersion")
  detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-libraries:$detektVersion")
}

allOpen {
  annotation("org.springframework.stereotype.Component")
}

tasks.test {
  useJUnitPlatform()
  outputs.dir("$buildDir/spock-reports")
}

tasks.withType<JacocoCoverageVerification> {
  violationRules {
    rule {
      limit {
        counter = "INSTRUCTION"
        minimum = "0.80".toBigDecimal()
      }
    }
    rule {
      limit {
        counter = "BRANCH"
        minimum = "0.70".toBigDecimal()
      }
    }
  }
}

tasks.withType<JacocoReport> {
  reports {
    html.outputLocation.set(file("${layout.buildDirectory.get()}/reports/jacoco/html"))
  }
}

tasks.detekt {
  reports {
    xml.required.set(true)
    html.required.set(true)
    xml.outputLocation.set(file("$buildDir/reports/detekt.xml"))
    html.outputLocation.set(file("$buildDir/reports/detekt.html"))
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

detekt {
  buildUponDefaultConfig = true
  parallel = true
  config = files("$rootDir/config/detekt/detekt.yml")
  baseline = file("$rootDir/config/detekt/baseline.xml")
}

codenarc {
  configFile = file("$rootDir/config/codenarc/codenarc.xml")
}

tasks.check.configure {
  dependsOn(tasks.jacocoTestReport)
  dependsOn(tasks.jacocoTestCoverageVerification)
}

apply(plugin = "com.vanniktech.maven.publish")

tasks.dokkaHtmlPartial.configure {
  failOnWarning.set(true)
}

signing {
  // This is required to allow using the signing key via the CI in ASCII armored format.
  // https://docs.gradle.org/current/userguide/signing_plugin.html#sec:in-memory-keys
  if (!project.gradle.startParameter.taskNames.any {
      it.contains("publishToMavenLocal") ||
          it.contains("publishMavenPublicationToMavenLocal")
    }) {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
  }
}
