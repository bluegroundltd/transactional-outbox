package io.github.bluegroundltd.springoutbox.database

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Version
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import java.time.Instant

@MappedSuperclass
abstract class
UpdatableEntity<ID> : PersistableEntity<ID>() {

  @Version
  @Column(name = "version")
  var objectVersion: Long? = null

  @LastModifiedDate
  @Column(name = "updated_at")
  var updatedAt: Instant? = null

  @LastModifiedBy
  @Column(name = "updated_by")
  var updatedBy: Long? = null
}
