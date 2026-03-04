package generics

// ============================================================
// KOTLIN GENERICS & VARIANCE — Full Runnable Example
// Run this in IntelliJ IDEA or https://play.kotlinlang.org
// ============================================================

fun main() {
    println("\n========== 1. TYPE HIERARCHY ==========")
    typeHierarchyDemo()

    println("\n========== 2. OUT — COVARIANCE (Producer) ==========")
    covarianceDemo()

    println("\n========== 3. IN — CONTRAVARIANCE (Consumer) ==========")
    contravarianceDemo()

    println("\n========== 4. INVARIANCE (Both Producer & Consumer) ==========")
    invarianceDemo()

    println("\n========== 5. USE-SITE VARIANCE ==========")
    useSiteVarianceDemo()

    println("\n========== 6. STAR PROJECTION ==========")
    starProjectionDemo()

    println("\n========== 7. GENERIC FUNCTIONS ==========")
    genericFunctionsDemo()

    println("\n========== 8. MULTIPLE CONSTRAINTS (where) ==========")
    multipleConstraintsDemo()

    println("\n========== 9. REAL WORLD — REPOSITORY PATTERN ==========")
    repositoryPatternDemo()
}

// ============================================================
// 1. TYPE HIERARCHY SETUP
// ============================================================

open class Animal(val name: String) {
    open fun sound() = "..."
    override fun toString() = "generics.Animal($name)"
}

class Dog(name: String) : Animal(name) {
    override fun sound() = "Woof"
    override fun toString() = "generics.Dog($name)"
}

class Cat(name: String) : Animal(name) {
    override fun sound() = "Meow"
    override fun toString() = "generics.Cat($name)"
}

fun typeHierarchyDemo() {
    val dog = Dog("Rex")
    val cat = Cat("Whiskers")
    val animal: Animal = dog   // ✅ generics.Dog IS-A generics.Animal

    println("generics.Dog: $dog, sound: ${dog.sound()}")
    println("generics.Cat: $cat, sound: ${cat.sound()}")
    println("generics.Animal ref pointing to generics.Dog: $animal")
}

// ============================================================
// 2. OUT — COVARIANCE (Producer)
// Rule: generics.Box<generics.Dog> can be used as generics.Box<generics.Animal>
// Direction: Child → Parent
// ============================================================

// 'out T' means: this class only PRODUCES T, never consumes it
class Box<out T>(private val item: T) {
    fun getItem(): T = item     // ✅ can give T out
    // fun setItem(t: T) { }   // ❌ cannot take T in — won't compile
}

// Read-only dispenser — only gives values out
class Dispenser<out T>(private vararg val items: T) {
    private var index = 0
    fun dispense(): T? = if (index < items.size) items[index++] else null
    fun isEmpty() = index >= items.size
}

fun covarianceDemo() {
    // generics.Box<generics.Dog> assigned to generics.Box<generics.Animal> ✅
    val dogBox: Box<Dog> = Box(Dog("Rex"))
    val animalBox: Box<Animal> = dogBox  // ✅ works because of 'out'
    println("From animalBox: ${animalBox.getItem()}")

    // generics.Dispenser<generics.Dog> used as generics.Dispenser<generics.Animal>
    val dogDispenser: Dispenser<Dog> = Dispenser(Dog("Buddy"), Dog("Max"))
    val animalDispenser: Dispenser<Animal> = dogDispenser  // ✅

    while (!animalDispenser.isEmpty()) {
        println("Dispensed: ${animalDispenser.dispense()}")
    }

    // This is why List<generics.Dog> works as List<generics.Animal>
    // Kotlin's List is declared as List<out E>
    val dogs: List<Dog> = listOf(Dog("A"), Dog("B"))
    val animals: List<Animal> = dogs  // ✅ List is covariant
    println("Animals from dog list: $animals")
}

// ============================================================
// 3. IN — CONTRAVARIANCE (Consumer)
// Rule: generics.Printer<generics.Animal> can be used as generics.Printer<generics.Dog>
// Direction: Parent → Child
// ============================================================

// 'in T' means: this class only CONSUMES T, never produces it
class Printer<in T> {
    fun print(item: T) = println("Printing: $item")  // ✅ consumes T
    // fun getItem(): T { }                           // ❌ cannot produce T
}

// generics.Shelter accepts animals — only consumes them
class Shelter<in T : Animal> {
    private val residents = mutableListOf<Animal>()

    fun accept(animal: T) {          // ✅ consumes T
        residents.add(animal)
        println("Accepted: $animal")
    }

    fun count() = residents.size
}

fun contravarianceDemo() {
    // generics.Printer<generics.Animal> used as generics.Printer<generics.Dog> ✅
    val animalPrinter: Printer<Animal> = Printer()
    val dogPrinter: Printer<Dog> = animalPrinter  // ✅ works because of 'in'

    dogPrinter.print(Dog("Rex"))    // prints fine
    dogPrinter.print(Dog("Buddy"))  // prints fine

    // generics.Shelter<generics.Animal> used as generics.Shelter<generics.Dog>
    val animalShelter: Shelter<Animal> = Shelter()
    val dogShelter: Shelter<Dog> = animalShelter  // ✅

    dogShelter.accept(Dog("Max"))
    dogShelter.accept(Dog("Luna"))
    println("Dogs in shelter: ${animalShelter.count()}")
}

// ============================================================
// 4. INVARIANCE — Both Producer & Consumer
// Rule: NO substitution in either direction
// ============================================================

// No variance keyword = invariant
// Can both produce AND consume T
class Storage<T> {
    private val items = mutableListOf<T>()

    fun store(item: T) = items.add(item)   // consumes T
    fun retrieve(index: Int): T = items[index]  // produces T
    fun size() = items.size
    override fun toString() = "generics.Storage$items"
}

fun invarianceDemo() {
    val dogStorage: Storage<Dog> = Storage()
    dogStorage.store(Dog("Rex"))

    // ❌ This would NOT compile — uncomment to see error:
    // val animalStorage: generics.Storage<generics.Animal> = dogStorage

    // WHY is this dangerous? Let's show with MutableList:
    val dogList: MutableList<Dog> = mutableListOf(Dog("Rex"))

    // ❌ This would NOT compile — uncomment to see the danger:
    // val animalList: MutableList<generics.Animal> = dogList
    // animalList.add(generics.Cat("Whiskers"))  // generics.Cat in generics.Dog list! 💥
    // val dog: generics.Dog = dogList[1]        // ClassCastException! 💥

    println("generics.Dog storage: $dogStorage")
    println("MutableList IS invariant — no substitution allowed ✅")

    // BUT read-only List IS covariant — safe!
    val readOnlyDogs: List<Dog> = dogList
    val readOnlyAnimals: List<Animal> = readOnlyDogs  // ✅ safe!
    println("Read-only List allows covariance: $readOnlyAnimals")
}

// ============================================================
// 5. USE-SITE VARIANCE
// When you don't own/control the class declaration
// Apply variance at the point of USE
// ============================================================

// copyFrom: source only gives values OUT
// copyTo:   dest only takes values IN
fun <T> copyItems(
    source: MutableList<out T>,  // 'out' at use-site = read only
    dest: MutableList<in T>      // 'in' at use-site = write only
) {
    for (item in source) {
        dest.add(item)           // ✅ safe!
        println("Copied: $item")
    }
}

fun useSiteVarianceDemo() {
    val dogs: MutableList<Dog> = mutableListOf(Dog("Rex"), Dog("Buddy"))
    val animals: MutableList<Animal> = mutableListOf()

    println("Before copy — animals: $animals")
    copyItems(dogs, animals)  // ✅ works with use-site variance
    println("After copy — animals: $animals")
}

// ============================================================
// 6. STAR PROJECTION
// When you don't care about the specific type
// ============================================================

fun printAnything(list: List<*>) {        // List of anything
    list.forEach { println("Item: $it") } // reads as Any?
}

fun describeMap(map: Map<String, *>) {
    map.forEach { (key, value) ->
        println("$key → $value (${value?.javaClass?.simpleName})")
    }
}

fun starProjectionDemo() {
    val ints = listOf(1, 2, 3)
    val strings = listOf("a", "b", "c")
    val dogs = listOf(Dog("Rex"), Dog("Buddy"))

    println("Printing ints:")
    printAnything(ints)

    println("Printing strings:")
    printAnything(strings)

    println("Printing dogs:")
    printAnything(dogs)

    println("Describing map:")
    describeMap(mapOf("name" to "Rex", "age" to 3, "isDog" to true))

    // ⚠️ Star projection limitation:
    val starList: MutableList<*> = mutableListOf(1, 2, 3)
    println("Can read: ${starList[0]}")   // ✅ returns Any?
    // starList.add(4)                    // ❌ cannot add — type unknown
}

// ============================================================
// 7. GENERIC FUNCTIONS
// ============================================================

// Basic generic function
fun <T> wrapInList(item: T): List<T> = listOf(item)

// Generic function with upper bound constraint
fun <T : Animal> makeSound(animal: T): String {
    return "${animal.name} says ${animal.sound()}"
}

// Generic extension function
fun <T> List<T>.secondOrNull(): T? = if (size >= 2) this[1] else null

// Generic function returning transformed type
fun <T, R> transformList(
    items: List<T>,
    transform: (T) -> R   // higher order function with generics
): List<R> = items.map(transform)

fun genericFunctionsDemo() {
    // generics.wrapInList
    val wrappedDog: List<Dog> = wrapInList(Dog("Rex"))
    val wrappedInt: List<Int> = wrapInList(42)
    println("Wrapped: $wrappedDog")
    println("Wrapped: $wrappedInt")

    // generics.makeSound with upper bound
    println(makeSound(Dog("Rex")))
    println(makeSound(Cat("Whiskers")))

    // extension function
    val animals = listOf(Dog("A"), Dog("B"), Dog("C"))
    println("Second: ${animals.secondOrNull()}")
    println("Second of empty: ${emptyList<Dog>().secondOrNull()}")

    // generics.transformList
    val names: List<String> = transformList(animals) { it.name }
    val sounds: List<String> = transformList(animals) { it.sound() }
    println("Names: $names")
    println("Sounds: $sounds")
}

// ============================================================
// 8. MULTIPLE CONSTRAINTS (where keyword)
// ============================================================

// T must be BOTH an generics.Animal AND Comparable
fun <T> findLargest(items: List<T>): T?
        where T : Animal,
              T : Comparable<T> {
    return items.maxOrNull()
}

// Comparable generics.Dog for demo
class ComparableDog(name: String) : Animal(name), Comparable<ComparableDog> {
    override fun compareTo(other: ComparableDog): Int =
        this.name.compareTo(other.name)  // compare alphabetically

    override fun toString() = "generics.ComparableDog($name)"
}

fun multipleConstraintsDemo() {
    val dogs = listOf(
        ComparableDog("Rex"),
        ComparableDog("Buddy"),
        ComparableDog("Zeus")
    )

    val largest = findLargest(dogs)
    println("Alphabetically largest dog name: $largest")
}

// ============================================================
// 9. REAL WORLD — GENERIC REPOSITORY PATTERN
// Simulates a clean architecture generics.Repository in Android
// ============================================================

// Base entity interface
interface Entity {
    val id: Int
}

// Generic generics.Result wrapper (like sealed class in production)
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

// Generic generics.Repository interface
interface Repository<T : Entity> {
    fun getById(id: Int): Result<T>
    fun getAll(): Result<List<T>>
    fun save(item: T): Result<T>
    fun delete(id: Int): Result<Boolean>
}

// Concrete entity
data class User(
    override val id: Int,
    val name: String,
    val email: String
) : Entity

data class Product(
    override val id: Int,
    val name: String,
    val price: Double
) : Entity

// Generic in-memory implementation
class InMemoryRepository<T : Entity> : Repository<T> {
    private val storage = mutableMapOf<Int, T>()

    override fun getById(id: Int): Result<T> {
        val item = storage[id]
        return if (item != null)
            Result.Success(item)
        else
            Result.Error("Item with id $id not found")
    }

    override fun getAll(): Result<List<T>> =
        Result.Success(storage.values.toList())

    override fun save(item: T): Result<T> {
        storage[item.id] = item
        return Result.Success(item)
    }

    override fun delete(id: Int): Result<Boolean> {
        val removed = storage.remove(id) != null
        return if (removed)
            Result.Success(true)
        else
            Result.Error("Item with id $id not found")
    }
}

// Generic function to handle results — uses 'out' variance
fun <T> handleResult(result: Result<T>, onSuccess: (T) -> Unit) {
    when (result) {
        is Result.Success -> onSuccess(result.data)
        is Result.Error -> println("❌ Error: ${result.message}")
        is Result.Loading -> println("⏳ Loading...")
    }
}

fun repositoryPatternDemo() {
    // generics.User repository
    val userRepo: Repository<User> = InMemoryRepository()

    handleResult(userRepo.save(User(1, "Alice", "alice@email.com"))) {
        println("✅ Saved: $it")
    }
    handleResult(userRepo.save(User(2, "Bob", "bob@email.com"))) {
        println("✅ Saved: $it")
    }
    handleResult(userRepo.getById(1)) {
        println("✅ Found: $it")
    }
    handleResult(userRepo.getAll()) {
        println("✅ All users: $it")
    }
    handleResult(userRepo.delete(99)) {
        println("✅ Deleted: $it")
    }

    println()

    // generics.Product repository — SAME generic repo, different type!
    val productRepo: Repository<Product> = InMemoryRepository()

    handleResult(productRepo.save(Product(1, "Laptop", 999.99))) {
        println("✅ Saved: $it")
    }
    handleResult(productRepo.getById(1)) {
        println("✅ Found: $it")
    }
}

// *
// ## 🎯 The Full SOLID + Variance Map
//SOLID Principle    Kotlin Variance Connection
//───────────────────────────────────────────────
//LSP          →  Box<Dog> safely substitutes Box<Animal>
//                only guaranteed by 'out' ✅
//
//ISP          →  Split Readable<out T> and Writeable<in T>
//                each interface has ONE concern ✅
//
//OCP          →  Generic classes open for extension (new types)
//                closed for modification (type safety enforced) ✅
//
// *
