# Transactional Outbox Library :: Spring Module

Transactional Outbox Spring is a reference implementation of the `core` library interfaces, using Spring framework and Postgres,
both as a storage and a locks' provider.

API Docs: https://bluegroundltd.github.io/transactional-outbox/spring/index.html

## Table of Contents

- [Installation](#installation)
- [Usage](#usage)
  - [Creating an Outbox library instance](#creating-an-outbox-library-instance)
  - [Creating your OutboxType](#creating-your-outboxtype)
  - [Creating your OutboxTypeConverter](#creating-your-outboxtypeconverter)
  - [Underlying Components](#underlying-components)
    - [Database Table](#database-table)
    - [Outbox Scheduler and Cleaner](#outbox-scheduler-and-cleaner)
    - [Postgres Locks](#postgres-locks)
- [Local Development](#local-development)
- [Publishing](#publishing)
  - [Publish via GitHub](#publish-via-github)
    - [Tag formatting](#tag-formatting)
  - [Publish via your workstation](#publish-via-your-workstation)

## Installation

Transactional Outbox is published on `mavenCentral`. In order to use it just add the following dependency:

```gradle

implementation("io.github.bluegroundltd:transactional-outbox-spring:0.0.1")

```

## Usage

### Creating an Outbox library instance

When you have an operation that you want to be executed asynchronously you can start by configuring the `Outbox`:

```kotlin
@Configuration
@ComponentScan(basePackages = ["io.github.bluegroundltd.springoutbox"])
@EntityScan(basePackages = ["io.github.bluegroundltd.springoutbox"])
@EnableJpaRepositories(basePackages = ["io.github.bluegroundltd.springoutbox"])
class TransactionalOutboxConfiguration(
  private val springTransactionalOutboxBuilder: SpringTransactionalOutboxBuilder,
  private val clock: Clock,
  private val outboxHandlers: Set<OutboxHandler>,
  @Value("\${app.outbox.locks.monitorIdentifier:42}")
  private val monitorLocksIdentifier: Long,
  @Value("\${app.outbox.locks.cleanupIdentifier:43}")
  private val cleanupLocksIdentifier: Long,
) : TransactionalOutboxInstanceHolder {

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(TransactionalOutboxConfiguration::class.java)
  }

  private lateinit var transactionalOutbox: TransactionalOutbox

  override fun getTransactionalOutbox(): TransactionalOutbox =
    transactionalOutbox

  @PostConstruct
  fun postConstruct() {
    transactionalOutbox = createTransactionalOutbox()
  }

  private fun createTransactionalOutbox(): TransactionalOutbox {
    logger.info(
      """
      Initializing outbox with handler(s) [${outboxHandlers.joinToString { it.javaClass.simpleName }}],
      monitor locks identifier "$monitorLocksIdentifier", cleanup locks identifier "$cleanupLocksIdentifier".
      """
        .trimIndent()
    )

    return springTransactionalOutboxBuilder
      .withClock(clock)
      .withHandlers(outboxHandlers)
      .withMonitorLocksIdentifier(monitorLocksIdentifier)
      .withCleanupLocksIdentifier(cleanupLocksIdentifier)
      .build()
  }
}
```

### Creating your OutboxType

Then you should add your `OutboxType` enumerations.
```kotlin
enum class MyOutboxType : OutboxType {
  OUTBOX_TYPE_ACTION;

  override fun getType(): String = name
}
```

### Creating your OutboxTypeConverter

After that you should define how a string representation should be converted to your `MyOutboxType`. A sample converter:
```kotlin
@Component
class MyOutboxTypeConverter : OutboxTypeConverter {
  override fun convert(source: String): OutboxType =
    MyOutboxType.valueOf(source)
}
```

### Underlying Components

#### Database Table
The database entity can be found here: [OutboxItemEntity](./src/main/kotlin/io/github/bluegroundltd/springoutbox/database/OutboxItemEntity.kt)

In order for it to work [this sql migration script](./src/main/resources/outbox_table_creation.sql) should be applied to your database.

#### Outbox Scheduler and Cleaner

The outbox is scheduled to run periodically based on the spring `@Scheduled` interface, and the same flow is used for the
cleanup job.

You can modify their parameters using the following application properties:
```yaml
jobs:
  transactionalOutbox:
    scheduler:
      enabled: false
      cron: 0 * * * * ?
    cleaner:
      enabled: false
      cron: 30 * * * * ?
```

If you want to **manage the outbox with your own scheduler and cleaner** all you have to do is create a bean that implements
the `OutboxScheduler` and `OutboxCleaner` interfaces.

If you want to **shut down the outbox yourself** all you have to do is create a bean that implements the `OutboxShutdown` interface.

#### Postgres Locks

In order to support coordination among multiple instances of applications using the outbox library, the library uses advisory locks.

Usage of the locks is achieved through:
```sql
select 1 from pg_advisory_lock(:id)
```
and
```sql
select 1 from pg_advisory_unlock(:id)
```


## Local Development
If you're working on a new version of the `spring` module, and you want this version to be available to other project before
publishing it, you can do so in two ways

* Alternative 1: Install the new version to your local Maven (m2) repo, using the `-SNAPSHOT` suffix **if you don't have signing keys.**
```shell
$ ./gradlew spring:publishToMavenLocal
```
then depend on the `x.y.z-SNAPSHOT` version as usual.
```gradle
implementation("io.github.bluegroundltd:transactional-outbox-spring:x.y.z-SNAPSHOT")
```
* Alternative 2: Change your dependencies to directly reference the jar file
```gradle
implementation(files("../transactional-outbox/spring/build/libs/transactional-outbox.jar"))
```

* Alternative 3: You can publish a snapshot version of the library and make it available to maven snapshot repository.
  1) Update the version in `gradle.properties` to a snapshot version, e.g. `0.0.1-SNAPSHOT`
  2) Publish it using the instructions here: [Publish via your workstation](#publish-via-your-workstation)
  3) Snapshot will be published to the maven snapshot repository and then you can use it in your project by adding the following to your `build.gradle` file:
  ```gradle
    repositories {
      maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        credentials {
          username = <your_username> or <your_gpr_user>
          password = <your_password> or <your_gpr_token>
        }
      }
    }
    dependencies {
        implementation("io.github.bluegroundltd:transactional-outbox-spring:0.0.1-SNAPSHOT")
    }
  ```

## Publishing

Firstly, bump version in `gradle.properties` of `spring` module, commit and push a PR. Once it gets merged, follow one of the two options below.


Now, you can either: 
1. publish via GitHub, or
2. locally from your machine

### Publish via GitHub
Using this method has the benefit of not having to provide any secrets whatsoever.  
Simply, push a git tag **after** a PR is merged, which will trigger the 
[release-spring.yml](../.github/workflows/release-spring.yml) pipeline.  
Said pipeline, will publish the artifact.

Please note that this will be automated in future work.

#### Tag formatting
To tag, please follow a format like `spring-v0.4.0`, that is prefixed with `spring-v` and the version number of the artifact you're 
about to release.  

### Publish via your workstation

* Having built the artifact
* Execute the following to upload artifact:
```shell
$ ./gradlew spring:publishAllPublicationsToMavenCentral \
            --no-configuration-cache \
            -Psigning.secretKeyRingFile=<keyring_file_path> \
            -Psigning.password=<keyring_password> \
            -Psigning.keyId=<keyring_id> \
            -PmavenCentralUsername=<nexus_username> \ 
            -PmavenCentralPassword=<nexus_password>
```

Note that if you maintain the properties above (`-P`) in your local `gradle.properties` file, you can omit them from the
command.
