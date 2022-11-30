description = "Atlas :: transactional-outbox"

plugins {
  kotlin("jvm")
  id("io.gitlab.arturbosch.detekt")
}

val detektVersion: String by project
val kotlinVersion: String by project
val slf4jVersion: String = "1.7.36"
val spockVersion: String by project
val spockReportsVersion: String by project

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
  implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
  implementation("org.slf4j:slf4j-api:${slf4jVersion}")

  testImplementation("org.spockframework:spock-core:$spockVersion")
  testImplementation("com.athaydes:spock-reports:$spockReportsVersion")
  testRuntimeOnly("org.slf4j:slf4j-simple:${slf4jVersion}")

  detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:$detektVersion")
}

tasks.test {
  useJUnitPlatform()
  systemProperty("com.athaydes.spockframework.report.outputDir", "$buildDir/spock-reports")
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
