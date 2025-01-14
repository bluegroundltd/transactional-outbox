package io.github.bluegroundltd.springoutbox.database

import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class PersistableEntity<ID> {

  @CreatedDate
  @Column(name = "created_at", updatable = false)
  var createdAt: Instant? = null

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  var createdBy: Long? = null
}
