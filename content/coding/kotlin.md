# Kotlin — coding task bank

> Hands-on implementation exercises. Each task has a skeleton, tests, model solution, and grading criteria. Tasks map to Bloom levels and align with the Q&A bank in `content/topics/kotlin.md`.
>
> **Beginner track (CT-KT-B series):** one concept per task, 5-15 min each, designed for Java devs touching Kotlin for the first time. Do these BEFORE the regular CT-KT-0XX tasks.

---

## CT-KT-B01 [bloom: recall] [difficulty: trivial]
**Task:** val vs var — fix the broken code
**Concept:** val/var, type inference, read-only references
**Requirements:**
1. A broken function is given with intentional val/var errors. Fix it so tests pass.
2. Do NOT change function signatures or test code — only fix the body.
**Skeleton:**
```kotlin
package com.major.playground.exercises

fun greet(name: String): String {
    var greeting = "Hello"
    greeting = "$greeting, $name!"
    return greeting
}

fun counter(): Int {
    val count = 0
    count = count + 1
    count = count + 1
    count = count + 1
    return count
}

fun buildFullName(first: String, last: String): String {
    var fullName: String
    fullName = "$first $last"
    fullName = fullName.uppercase()
    return fullName
}
```
**Tests:**
```kotlin
package com.major.playground.exercises

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CT_KT_B01Test {

    @Test
    fun `greet returns greeting with name`() {
        assertEquals("Hello, World!", greet("World"))
    }

    @Test
    fun `counter returns 3`() {
        assertEquals(3, counter())
    }

    @Test
    fun `buildFullName returns uppercase full name`() {
        assertEquals("JOHN DOE", buildFullName("John", "Doe"))
    }
}
```
**Model solution:**
```kotlin
package com.major.playground.exercises

fun greet(name: String): String {
    val greeting = "Hello"
    val result = "$greeting, $name!"
    return result
}

fun counter(): Int {
    var count = 0
    count = count + 1
    count = count + 1
    count = count + 1
    return count
}

fun buildFullName(first: String, last: String): String {
    val fullName = "$first $last"
    return fullName.uppercase()
}
```
**Grading criteria:**
- correct: greet uses val (no reassignment needed), counter uses var (needs mutation), buildFullName uses val with chaining. All tests pass.
- correct_with_gap: tests pass but uses var everywhere (missed the point — val by default)
- partial: some fixed, some still broken
- incorrect: doesn't compile

---

## CT-KT-B02 [bloom: recall] [difficulty: trivial]
**Task:** Null safety — make the chain safe
**Concept:** `?.`, `?:`, nullable types
**Requirements:**
1. Given a chain of nullable lookups, make each function null-safe without using `!!`
2. No if-null checks allowed — use operators only
**Skeleton:**
```kotlin
package com.major.playground.exercises

data class Company(val name: String, val ceo: Employee?)
data class Employee(val name: String, val email: String?)

fun getCeoEmail(company: Company?): String? {
    TODO("Return CEO's email using safe calls, or null if anything missing")
}

fun getCeoEmailOrDefault(company: Company?): String {
    TODO("Return CEO's email, or 'no-email@company.com' if anything is null")
}

fun getCeoNameLength(company: Company?): Int {
    TODO("Return length of CEO's name, or 0 if company or CEO is null")
}
```
**Tests:**
```kotlin
package com.major.playground.exercises

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CT_KT_B02Test {

    private val fullCompany = Company("Acme", Employee("Alice", "alice@acme.com"))
    private val noEmailCeo = Company("Acme", Employee("Bob", null))
    private val noCeo = Company("Acme", null)

    @Test
    fun `getCeoEmail returns email when present`() {
        assertEquals("alice@acme.com", getCeoEmail(fullCompany))
    }

    @Test
    fun `getCeoEmail returns null when email missing`() {
        assertNull(getCeoEmail(noEmailCeo))
    }

    @Test
    fun `getCeoEmail returns null when ceo missing`() {
        assertNull(getCeoEmail(noCeo))
    }

    @Test
    fun `getCeoEmail returns null when company null`() {
        assertNull(getCeoEmail(null))
    }

    @Test
    fun `getCeoEmailOrDefault returns email when present`() {
        assertEquals("alice@acme.com", getCeoEmailOrDefault(fullCompany))
    }

    @Test
    fun `getCeoEmailOrDefault returns default when anything null`() {
        assertEquals("no-email@company.com", getCeoEmailOrDefault(noCeo))
        assertEquals("no-email@company.com", getCeoEmailOrDefault(null))
        assertEquals("no-email@company.com", getCeoEmailOrDefault(noEmailCeo))
    }

    @Test
    fun `getCeoNameLength returns length when present`() {
        assertEquals(5, getCeoNameLength(fullCompany))
    }

    @Test
    fun `getCeoNameLength returns 0 when null`() {
        assertEquals(0, getCeoNameLength(noCeo))
        assertEquals(0, getCeoNameLength(null))
    }
}
```
**Model solution:**
```kotlin
package com.major.playground.exercises

data class Company(val name: String, val ceo: Employee?)
data class Employee(val name: String, val email: String?)

fun getCeoEmail(company: Company?): String? =
    company?.ceo?.email

fun getCeoEmailOrDefault(company: Company?): String =
    company?.ceo?.email ?: "no-email@company.com"

fun getCeoNameLength(company: Company?): Int =
    company?.ceo?.name?.length ?: 0
```
**Grading criteria:**
- correct: one-liner safe call chains, Elvis for defaults, no `!!`, all tests pass
- correct_with_gap: works but verbose (multiple lines, intermediate variables)
- partial: some functions work, others crash on null
- incorrect: uses `!!` or if-null blocks

---

## CT-KT-B03 [bloom: recall] [difficulty: easy]
**Task:** when expression — translate if-else to when
**Concept:** `when` as expression, pattern matching, ranges
**Requirements:**
1. Rewrite the given if-else chain as a `when` expression
2. Each function must use `when` (not if-else)
**Skeleton:**
```kotlin
package com.major.playground.exercises

fun httpStatusCategory(code: Int): String {
    TODO("Use when with ranges: 1xx=Informational, 2xx=Success, 3xx=Redirect, 4xx=Client Error, 5xx=Server Error, else=Unknown")
}

fun describeType(value: Any): String {
    TODO("Use when with is-checks: String, Int, Boolean, List<*>, else=Unknown type")
}

fun dayType(day: String): String {
    TODO("Use when: Monday-Friday=Workday, Saturday/Sunday=Weekend, else=Invalid")
}
```
**Tests:**
```kotlin
package com.major.playground.exercises

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CT_KT_B03Test {

    @Test
    fun `httpStatusCategory categorizes correctly`() {
        assertEquals("Informational", httpStatusCategory(100))
        assertEquals("Success", httpStatusCategory(200))
        assertEquals("Success", httpStatusCategory(204))
        assertEquals("Redirect", httpStatusCategory(301))
        assertEquals("Client Error", httpStatusCategory(404))
        assertEquals("Server Error", httpStatusCategory(500))
        assertEquals("Unknown", httpStatusCategory(600))
    }

    @Test
    fun `describeType identifies types`() {
        assertEquals("String", describeType("hello"))
        assertEquals("Int", describeType(42))
        assertEquals("Boolean", describeType(true))
        assertEquals("List", describeType(listOf(1, 2)))
        assertEquals("Unknown type", describeType(3.14))
    }

    @Test
    fun `dayType classifies days`() {
        assertEquals("Workday", dayType("Monday"))
        assertEquals("Workday", dayType("Friday"))
        assertEquals("Weekend", dayType("Saturday"))
        assertEquals("Weekend", dayType("Sunday"))
        assertEquals("Invalid", dayType("Funday"))
    }
}
```
**Model solution:**
```kotlin
package com.major.playground.exercises

fun httpStatusCategory(code: Int): String = when (code) {
    in 100..199 -> "Informational"
    in 200..299 -> "Success"
    in 300..399 -> "Redirect"
    in 400..499 -> "Client Error"
    in 500..599 -> "Server Error"
    else -> "Unknown"
}

fun describeType(value: Any): String = when (value) {
    is String -> "String"
    is Int -> "Int"
    is Boolean -> "Boolean"
    is List<*> -> "List"
    else -> "Unknown type"
}

fun dayType(day: String): String = when (day) {
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday" -> "Workday"
    "Saturday", "Sunday" -> "Weekend"
    else -> "Invalid"
}
```
**Grading criteria:**
- correct: all three use `when` as expression, ranges used for HTTP, is-checks for types, comma-grouping for days
- correct_with_gap: when used but verbose (separate branch per day instead of comma grouping)
- partial: some functions use when, others still if-else
- incorrect: no when expressions used

---

## CT-KT-B04 [bloom: understand] [difficulty: easy]
**Task:** Data class — model a domain object
**Concept:** data class, copy(), destructuring, toString
**Requirements:**
1. Create `data class Product(val id: Long, val name: String, val price: BigDecimal, val active: Boolean)`
2. Write `deactivate(product: Product): Product` — returns copy with active=false
3. Write `applyDiscount(product: Product, percent: Int): Product` — returns copy with reduced price
4. Write `toLabel(product: Product): String` — use destructuring to return `"[id] name - price"`
**Skeleton:**
```kotlin
package com.major.playground.exercises

import java.math.BigDecimal

// TODO: define data class Product

fun deactivate(product: Product): Product {
    TODO("Return copy with active=false")
}

fun applyDiscount(product: Product, percent: Int): Product {
    TODO("Return copy with price reduced by percent")
}

fun toLabel(product: Product): String {
    TODO("Use destructuring to build label: [id] name - price")
}
```
**Tests:**
```kotlin
package com.major.playground.exercises

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class CT_KT_B04Test {

    private val laptop = Product(1L, "Laptop", BigDecimal("1000.00"), true)

    @Test
    fun `Product is a data class`() {
        val p1 = Product(1L, "X", BigDecimal("10.00"), true)
        val p2 = Product(1L, "X", BigDecimal("10.00"), true)
        assertEquals(p1, p2)
        assertEquals(p1.hashCode(), p2.hashCode())
    }

    @Test
    fun `deactivate returns inactive copy`() {
        val result = deactivate(laptop)
        assertFalse(result.active)
        assertEquals(laptop.name, result.name)
        assertTrue(laptop.active)
    }

    @Test
    fun `applyDiscount reduces price`() {
        val result = applyDiscount(laptop, 20)
        assertEquals(BigDecimal("800.00"), result.price)
        assertEquals(BigDecimal("1000.00"), laptop.price)
    }

    @Test
    fun `toLabel uses destructuring format`() {
        assertEquals("[1] Laptop - 1000.00", toLabel(laptop))
    }
}
```
**Model solution:**
```kotlin
package com.major.playground.exercises

import java.math.BigDecimal
import java.math.RoundingMode

data class Product(val id: Long, val name: String, val price: BigDecimal, val active: Boolean)

fun deactivate(product: Product): Product = product.copy(active = false)

fun applyDiscount(product: Product, percent: Int): Product {
    val factor = BigDecimal(100 - percent).divide(BigDecimal(100))
    return product.copy(price = product.price.multiply(factor).setScale(2, RoundingMode.HALF_UP))
}

fun toLabel(product: Product): String {
    val (id, name, price, _) = product
    return "[$id] $name - $price"
}
```
**Grading criteria:**
- correct: data class, copy() used, destructuring used, BigDecimal math correct, all tests pass
- correct_with_gap: works but creates new Product() instead of copy(), or skips destructuring
- partial: data class ok but functions buggy
- incorrect: not a data class or doesn't compile

---

## CT-KT-B05 [bloom: understand] [difficulty: easy]
**Task:** Extension functions — add behavior to existing types
**Concept:** extension functions, extension on collections, receiver type
**Requirements:**
1. Write `String.initials(): String` — "John Doe" -> "JD", "alice" -> "A"
2. Write `Int.isEven(): Boolean`
3. Write `List<Int>.secondOrNull(): Int?` — second element or null if list has < 2 elements
4. Write `String.removeWhitespace(): String` — remove all spaces and tabs
**Skeleton:**
```kotlin
package com.major.playground.exercises

// TODO: extension function String.initials()
// TODO: extension function Int.isEven()
// TODO: extension function List<Int>.secondOrNull()
// TODO: extension function String.removeWhitespace()
```
**Tests:**
```kotlin
package com.major.playground.exercises

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CT_KT_B05Test {

    @Test
    fun `initials extracts first letters`() {
        assertEquals("JD", "John Doe".initials())
        assertEquals("A", "alice".initials())
        assertEquals("ABC", "Anna Barbara Clark".initials())
    }

    @Test
    fun `initials handles empty string`() {
        assertEquals("", "".initials())
    }

    @Test
    fun `isEven works`() {
        assertTrue(4.isEven())
        assertFalse(3.isEven())
        assertTrue(0.isEven())
        assertFalse((-1).isEven())
    }

    @Test
    fun `secondOrNull returns second element`() {
        assertEquals(20, listOf(10, 20, 30).secondOrNull())
    }

    @Test
    fun `secondOrNull returns null for short lists`() {
        assertNull(listOf(10).secondOrNull())
        assertNull(emptyList<Int>().secondOrNull())
    }

    @Test
    fun `removeWhitespace strips spaces and tabs`() {
        assertEquals("HelloWorld", "Hello World".removeWhitespace())
        assertEquals("abc", " a\tb c ".removeWhitespace())
        assertEquals("", "   ".removeWhitespace())
    }
}
```
**Model solution:**
```kotlin
package com.major.playground.exercises

fun String.initials(): String =
    split(" ")
        .filter { it.isNotEmpty() }
        .map { it.first().uppercaseChar() }
        .joinToString("")

fun Int.isEven(): Boolean = this % 2 == 0

fun List<Int>.secondOrNull(): Int? = getOrNull(1)

fun String.removeWhitespace(): String = replace(Regex("[\\s\\t]"), "")
```
**Grading criteria:**
- correct: all extension functions, idiomatic, all tests pass
- correct_with_gap: works but verbose (e.g., manual loop in initials instead of split+map)
- partial: some work, others crash
- incorrect: defined as regular functions (not extensions) or doesn't compile

---

## CT-KT-B06 [bloom: understand] [difficulty: medium]
**Task:** Collections pipeline — filter, map, group, aggregate
**Concept:** filter, map, groupBy, mapValues, sortedBy, take
**Requirements:**
1. Given `data class Student(val name: String, val grade: Int, val score: Double)`
2. Write `List<Student>.passingStudents(): List<String>` — names of students with score >= 50.0, sorted alphabetically
3. Write `List<Student>.averageByGrade(): Map<Int, Double>` — average score per grade
4. Write `List<Student>.topPerGrade(): Map<Int, String>` — name of highest-scoring student per grade
**Skeleton:**
```kotlin
package com.major.playground.exercises

data class Student(val name: String, val grade: Int, val score: Double)

// TODO: List<Student>.passingStudents()
// TODO: List<Student>.averageByGrade()
// TODO: List<Student>.topPerGrade()
```
**Tests:**
```kotlin
package com.major.playground.exercises

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CT_KT_B06Test {

    private val students = listOf(
        Student("Alice", 1, 85.0),
        Student("Bob", 1, 45.0),
        Student("Charlie", 2, 92.0),
        Student("Diana", 2, 78.0),
        Student("Eve", 1, 60.0),
        Student("Frank", 2, 30.0),
    )

    @Test
    fun `passingStudents returns names sorted alphabetically`() {
        assertEquals(listOf("Alice", "Charlie", "Diana", "Eve"), students.passingStudents())
    }

    @Test
    fun `passingStudents on empty list`() {
        assertEquals(emptyList<String>(), emptyList<Student>().passingStudents())
    }

    @Test
    fun `averageByGrade computes correctly`() {
        val avg = students.averageByGrade()
        assertEquals(63.33, avg[1]!!, 0.01)
        assertEquals(66.67, avg[2]!!, 0.01)
    }

    @Test
    fun `topPerGrade finds highest scorer`() {
        val top = students.topPerGrade()
        assertEquals("Alice", top[1])
        assertEquals("Charlie", top[2])
    }
}
```
**Model solution:**
```kotlin
package com.major.playground.exercises

data class Student(val name: String, val grade: Int, val score: Double)

fun List<Student>.passingStudents(): List<String> =
    filter { it.score >= 50.0 }
        .map { it.name }
        .sorted()

fun List<Student>.averageByGrade(): Map<Int, Double> =
    groupBy { it.grade }
        .mapValues { (_, students) -> students.map { it.score }.average() }

fun List<Student>.topPerGrade(): Map<Int, String> =
    groupBy { it.grade }
        .mapValues { (_, students) -> students.maxBy { it.score }.name }
```
**Grading criteria:**
- correct: functional pipeline, extension functions, all tests pass
- correct_with_gap: works but uses imperative loops or mutable maps
- partial: some functions work, groupBy/mapValues not understood
- incorrect: doesn't compile or fundamentally wrong approach

---

## CT-KT-B07 [bloom: apply] [difficulty: medium]
**Task:** Sealed class + when — model API responses
**Concept:** sealed interface, exhaustive when, smart cast, data classes as subtypes
**Requirements:**
1. Define `sealed interface ApiResponse<out T>` with:
   - `data class Ok<T>(val data: T) : ApiResponse<T>`
   - `data class Error(val code: Int, val message: String) : ApiResponse<Nothing>`
   - `data object Loading : ApiResponse<Nothing>`
2. Write `describe(response: ApiResponse<*>): String` — exhaustive when, no else
3. Write `getOrDefault(response: ApiResponse<T>, default: T): T` — data from Ok, or default
**Skeleton:**
```kotlin
package com.major.playground.exercises

// TODO: sealed interface ApiResponse and subtypes

// TODO: fun describe(response: ApiResponse<*>): String

// TODO: fun <T> getOrDefault(response: ApiResponse<T>, default: T): T
```
**Tests:**
```kotlin
package com.major.playground.exercises

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CT_KT_B07Test {

    @Test
    fun `describe Ok`() {
        val r = Ok("hello")
        assertTrue(describe(r).contains("hello"))
    }

    @Test
    fun `describe Error`() {
        val r = Error(404, "Not Found")
        val d = describe(r)
        assertTrue(d.contains("404"))
        assertTrue(d.contains("Not Found"))
    }

    @Test
    fun `describe Loading`() {
        assertTrue(describe(Loading).lowercase().contains("loading"))
    }

    @Test
    fun `getOrDefault returns data for Ok`() {
        assertEquals("hello", getOrDefault(Ok("hello"), "fallback"))
    }

    @Test
    fun `getOrDefault returns default for Error`() {
        assertEquals("fallback", getOrDefault(Error(500, "fail"), "fallback"))
    }

    @Test
    fun `getOrDefault returns default for Loading`() {
        assertEquals(42, getOrDefault(Loading, 42))
    }
}
```
**Model solution:**
```kotlin
package com.major.playground.exercises

sealed interface ApiResponse<out T>
data class Ok<T>(val data: T) : ApiResponse<T>
data class Error(val code: Int, val message: String) : ApiResponse<Nothing>
data object Loading : ApiResponse<Nothing>

fun describe(response: ApiResponse<*>): String = when (response) {
    is Ok -> "OK: ${response.data}"
    is Error -> "Error ${response.code}: ${response.message}"
    is Loading -> "Loading..."
}

fun <T> getOrDefault(response: ApiResponse<T>, default: T): T = when (response) {
    is Ok -> response.data
    is Error -> default
    is Loading -> default
}
```
**Grading criteria:**
- correct: sealed interface, data object for Loading, exhaustive when, generics correct, all tests pass
- correct_with_gap: works but uses `object` instead of `data object`, or uses else branch
- partial: types defined but functions incomplete
- incorrect: no sealed type or doesn't compile
