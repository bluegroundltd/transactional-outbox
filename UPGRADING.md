# Upgrade Guide

## v.3.x.x

### v.3.0.0 - Java & Kotlin upgrade

Java was upgraded to version 17 and Kotlin to 1.9

## v.2.x.x

### v.2.3.3 - Error logging fix

Release 2.3.3 updates the main outbox processor to handle all Throwables and log them. Previously, only exceptions
were handled and logged.

### v.2.3.2 - Fix for legacy instant processing and insertion hints

Release 2.3.2 fixes the legacy instant processing (i.e. when `instantOrderingEnabled` is set to `false`) which was
broken since 2.3. The fix
introduces a flow where the outbox item is first inserted and then immediately updated for processing.

#### Persistence changes

The updated insertion implementation for instant processing introduces a flow where both `OutboxStore#insert` and
`OutboxStore#update` are called. In turn, this means that the corresponding implementations may need to be updated to
support it. Since this might have performance implications (e.g. requiring flushing the date to the underlying
database), the library now provides a set of hints (`OutboxInsertionHint`) to allow for implementors to appropriately
optimize. 

### v.2.3.0 - Support for grouping and ordering outbox items

Release 2.3.0 introduces support for grouping and ordering outbox items. Applications that want to take advantage of
the new features would need to:
- Implement the appropriate providers.
- Configure them through `withGroupIdProvider` and `withGroupingConfiguration` in `TransactionalOutboxBuilder`.
- Update implementations of `OutboxStore#fetch` and `OutboxStore#update`.

The functionality is **optional** and applications that do not wish to use it, can continue to use the library as
before.

#### Persistence changes (i.e. `OutboxItem` and `OutboxStore`)

A new field (`groupId`) has been added to the `OutboxItem` class. It allows for an arbitrary value which applications
may use to group outbox items together. The field has a default value of `null` to maintain backward compatibility but
implementors that would like to utilize it, should set/update it appropriately in `OutboxStore#fetch` and
`OutboxStore#update`.

In order to support the grouping and ordering functionality, the library needs to have access to **all the outbox items
that potentially belong to the same group**. This includes items that may not be eligible for processing (e.g.
scheduled for a later time, or failed). Accordingly, `OutboxStore#fetch` should be updated to return them. It is
envisioned that most implementations will first fetch the relevant items and then use the `groupId` value to also
retrieve items with the same value.

Furthermore, the library will now perform a runtime filtering of the fetched items so that it will only process the
ones that are actually eligible. Therefore, **it is no longer necessary** for the `OutboxStore#fetch` implementations
to filter out non-eligible items. However, implementors could still do so, to limit the amount of items returned and
improve performance (provided as described above that they also return all the same-group items).

#### Grouping and ordering providers

- `OutboxGroupIdProvider` should be implemented by all applications that want to use the `groupId` field for 
  identifying groups. The provider is invoked after adding an outbox item to the store and should return the group id
  value for the item or `null` if the item does not belong to a group. By default, the library utilizes a provider that
  always returns null.
- `OutboxGroupingProvider` allows for implementing a completely custom solution for grouping items. The provider is
  invoked after the items are fetched and should return a list of `OutboxItemGroup`s. Most applications won't need
  to implement this provider since by default the library utilizes a provider that automatically groups items based on
  the `groupId` field which should be good enough for most use cases.
- `OutboxOrderingProvider` is utilized by the default grouping provider to allow for custom ordering when using the
  built-in (i.e. `groupId` based) grouping implementation. The provider is invoked after the items are grouped and
  should return a list of `OutboxItem` in the desired order. By default, the library utilizes a provider that returns
  the items in the order they were provider.

### v.2.2.0 - Common implementation for scheduled and instant processing

Release 2.2.0 introduces the **option** to use the same implementation for handling both scheduled (i.e. `monitor`) and
instant (i.e. `processInstantOutbox`) processing of items. This is done by essentially having the latter call the former
with an additional parameter.

This is a step towards the implementation of grouping and ordering outbox items which would only be supported for
outbox items that are processed through the `monitor` method.

To support this functionality, `OutboxFilter` is enhanced with an optional `id` field. Implementors of the
`OutboxStore#fetch` method **could** use this value to only retrieve the relevant outbox item, effectively limiting the
amount of items returned and improving performance. However, **it is not required** to do so, as the library will
also filter for the requested item in memory. 

which is used to identify the outbox item
when calling the `monitor` method. The `id` is generated by the `OutboxHandler` when creating the outbox item.

#### Suggested Changes

The option for using the same implementation is configurable through
`TransactionalOutboxBuilder#withInstantOrderingEnabled`. If not configured, the value defaults to `false` in order to
minimize unexpected disruption to applications already using the transactional outbox. However, in the coming versions
this will change and the value will start defaulting to `true` in order to take advantage of the new functionality.
Therefore, any applications that wish to have this feature disabled, should explicitly set it.

The release, also marks `processInstantOutbox` as deprecated and will be removed in a following version. Applications
are encouraged to start using calling the `monitor` method directly with a hint of the outbox item id.

### v.2.0.0 - Completed outbox items cleanup coordination

Release 2.0.0 introduces coordination in the cleanup process across instances using locks.

#### `TransactionalOutboxBuilder#withLocksProvider` has been removed in favor of `withMonitorLocksProvider`   
The reason for this breaking change was the need to introduce a different locks provider for the completed outbox items cleanup process.  
We could reuse the same locks provider for both the monitor and cleanup process, but this would entail serialized execution of the two processes, which was not desirable.    
The requirement for the new locks provider is solely to be independent of the locks provider used in the monitor process.  
For example, if the locks provider implementation is using Postgres advisory locks, the monitor and the cleanup locks should use a different lock identifier.

**Required changes**  
The `TransactionalOutboxBuilder` call needs to be updated from 
```kotlin
TransactionalOutboxBuilder
  .make(clock)
  .withHandlers(outboxHandlers)
  .withLocksProvider(locksProvider)
  .withStore(outboxStore)
  .build()
```
to
```kotlin
TransactionalOutboxBuilder
  .make(clock)
  .withHandlers(outboxHandlers)
  .withMonitorLocksProvider(PostgresOutboxLocksProvider(LOCKS_MONITOR_ID))
  .withCleanupLocksProvider(PostgresOutboxLocksProvider(LOCKS_CLEANUP_ID))
  .withStore(outboxStore)
  .build()
```
N.B.: The above assumes that the locks provider implementation is using Postgres advisory locks.

## v.1.x.x 

### v.1.0.0 - Completed outbox items cleanup

Release 1.0 introduces a cleanup process for the outbox items that have been successfully processed, thus reducing the size of the outbox table, which can grow quite large.  
When the outbox items are processes successfully, in addition to be marked as completed, their `OutboxItem.deleteAfter` field is set to `now() + retentionPeriod`.  
The cleanup process, like monitor, should be run periodically, depending on your needs. Once run, it deletes the completed
outbox items whose `deleteAfter` is earlier than the current time.

It is advisable to manually delete the already completed outbox items before upgrading to 1.0.0, as the cleanup process
will issue a deletion, which may be quite heavy in terms of I/O operations, hence timeouts may occur on the first run.

**Required changes**  
In the `OutboxStore` implementing class, the `deleteCompletedItems(now: Instant)` method needs to be implemented.
The method should simply delete the outbox items with status `COMPLETED` with a `deleteAfter` earlier than the provided `now` parameter.

Finally, the retention duration period can be defined per outbox handler for flexibility.  
A new `OutboxHandler` method has been added `getRetentionDuration(): Duration` which should return the retention period for the outbox items of the handler.  
Feel free to look the [SimpleOutboxHandler](./core/src/main/kotlin/io/github/bluegroundltd/outbox/SimpleOutboxHandler.kt) for an example.
