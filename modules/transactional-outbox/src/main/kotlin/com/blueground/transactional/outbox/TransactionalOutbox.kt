package com.blueground.transactional.outbox

interface TransactionalOutbox {

  fun add()

  fun monitor()
}
