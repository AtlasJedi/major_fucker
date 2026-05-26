/*
 * Kotlin Spring Boot playground — build file.
 *
 * Key decisions documented here:
 *
 *  - kotlin("plugin.spring") auto-opens all @Component, @Configuration, @Service etc.
 *    Spring needs to subclass beans; without this plugin every class would need `open`
 *    manually. LESSON: Kotlin classes are final by default; Spring's CGLIB proxying
 *    requires open (or the plugin).
 *
 *  - kotlinx-coroutines-reactor bridges Kotlin coroutines and Project Reactor.
 *    This is what makes `suspend fun` work inside Spring WebFlux controllers.
 *
 *  - io.projectreactor:reactor-kotlin-extensions provides extension functions like
 *    `awaitSingle()`, `awaitBody()`, etc. on Reactor types.
 *
 *  - mockk:mockk replaces Mockito for idiomatic Kotlin mocking (no more `any()!!` hacks).
 *
 *  - kotlin-test-junit5 wires Kotlin's test infrastructure into JUnit 5.
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.major"
version = "0.0.1-SNAPSHOT"

// LESSON: Java toolchain — tells Gradle which JDK to use for compilation and runtime.
// JDK 21 is the current LTS. Allegro targets JVM 21 in production.
// Gradle downloads the right JDK if it isn't found locally (via Toolchains).
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // ────────────────────────────────────────────────────────────────────────
    // Core Spring WebFlux (reactive HTTP server on top of Netty, not Tomcat)
    // LESSON: WebFlux uses non-blocking I/O — a small thread pool handles many requests.
    //         Traditional Spring MVC uses one thread per request (Tomcat).
    // ────────────────────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // ────────────────────────────────────────────────────────────────────────
    // Coroutines — the Kotlin side of reactive programming
    // kotlinx-coroutines-reactor: bridges suspend/Flow <-> Mono/Flux
    // LESSON: Without this, Spring would not know how to call suspend functions.
    //         The magic is in CoroutinesUtils.invokeSuspendingFunction() inside Spring.
    // ────────────────────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")

    // Jackson Kotlin module — deserialises data classes without no-arg constructors
    // LESSON: Without this, Jackson can't map JSON to Kotlin data classes that have
    //         non-nullable primary constructor parameters.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // ────────────────────────────────────────────────────────────────────────
    // Test dependencies
    // ────────────────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        // LESSON: exclude Mockito so MockK is the only mock library on the classpath.
        //         Having both causes confusing import ambiguities.
        exclude(module = "mockito-core")
        exclude(module = "mockito-junit-jupiter")
    }
    testImplementation("io.projectreactor:reactor-test")
    // kotlinx-coroutines-test: provides runTest { } + TestCoroutineScheduler for virtual time
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    // MockK — idiomatic Kotlin mocking (type-safe, no `@Mock` field injection required)
    testImplementation("io.mockk:mockk:1.13.13")
    // Spring MockK integration for @MockkBean
    testImplementation("com.ninja-squad:springmockk:4.0.2")
}

// ────────────────────────────────────────────────────────────────────────────
// Kotlin compiler configuration
// ────────────────────────────────────────────────────────────────────────────
tasks.withType<KotlinCompile> {
    compilerOptions {
        // -Xjsr305=strict: makes Spring's @NonNull/@Nullable annotations respected
        //   by the Kotlin null-safety checker. Prevents NullPointerExceptions from
        //   Java interop that the compiler would otherwise not catch.
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
        )
        // LESSON: jvmTarget must match the toolchain Java version.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Show test output live (useful when studying failures)
    testLogging {
        events("passed", "skipped", "failed")
    }
}
