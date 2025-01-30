package io.github.bluegroundltd.springoutbox.utils

import kotlin.jvm.JvmClassMappingKt

trait KotlinUtils {

  def toKClass(Class<?> clazz) {
    JvmClassMappingKt.getKotlinClass(clazz)
  }
}