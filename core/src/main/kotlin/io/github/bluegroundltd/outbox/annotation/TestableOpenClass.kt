package io.github.bluegroundltd.outbox.annotation

/** Any class annotated with this method with be not final, and it can be Mocked for testing purposes
 *
 * @see [All-open compiler plugin](https://kotlinlang.org/docs/all-open-plugin.html)
 */
@Target(AnnotationTarget.CLASS)
annotation class TestableOpenClass
