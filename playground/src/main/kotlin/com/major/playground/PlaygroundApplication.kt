/**
 * REQUIREMENTS this file demonstrates:
 *   - Spring Boot application entry point with Kotlin idioms
 *   - Using runApplication<T>() instead of SpringApplication.run() — idiomatic Kotlin
 *
 * LESSONS embedded:
 *   - Why there is no `main` method inside a class (top-level functions in Kotlin)
 *   - @SpringBootApplication is @Configuration + @EnableAutoConfiguration + @ComponentScan
 *
 * RELATED DRILL TOPICS: kotlin_basics, spring_boot
 */
package com.major.playground

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

// LESSON: @SpringBootApplication is a meta-annotation that bundles three annotations:
//   1. @Configuration   — this class is a source of Spring bean definitions
//   2. @EnableAutoConfiguration — Spring Boot auto-configures beans based on classpath
//   3. @ComponentScan   — Spring scans this package (and sub-packages) for components
//
// The kotlin("plugin.spring") Gradle plugin marks this class as `open` automatically,
// which CGLIB needs to create runtime subclasses (proxy pattern for AOP, transactions, etc.)
@SpringBootApplication
class PlaygroundApplication

// LESSON: Top-level functions are a Kotlin feature with no Java equivalent.
// There is no wrapping class — the function lives directly in the package.
// runApplication<PlaygroundApplication>(*args) is sugar for:
//   SpringApplication.run(PlaygroundApplication::class.java, *args)
fun main(args: Array<String>) {
    runApplication<PlaygroundApplication>(*args)
}
