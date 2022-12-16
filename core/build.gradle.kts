description = "Transactional Outbox :: core"

plugins {
  // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
  kotlin("jvm")
  java
  groovy
  codenarc
  jacoco
  id("io.gitlab.arturbosch.detekt")
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
val jacksonVersion: String by project

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
  implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
  implementation("org.slf4j:slf4j-api:${slf4jVersion}")
  implementation("com.google.guava:guava:31.1-jre")

  testImplementation("org.spockframework:spock-core:$spockVersion")
  testImplementation("com.athaydes:spock-reports:$spockReportsVersion")
  testRuntimeOnly("net.bytebuddy:byte-buddy:1.12.19")
  testRuntimeOnly("org.objenesis:objenesis:3.3")
  testRuntimeOnly("org.slf4j:slf4j-simple:${slf4jVersion}")

  detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:$detektVersion")
  detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-libraries:$detektVersion")
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
        minimum = "0.95".toBigDecimal()
      }
    }
    rule {
      limit {
        counter = "BRANCH"
        minimum = "0.90".toBigDecimal()
      }
    }
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
  withJavadocJar()
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
