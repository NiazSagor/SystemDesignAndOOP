fun main() {
    val inventory = LibraryInventory()
    val borrowManager = LibraryBorrowManager(borrowLimit = 3, inventory = inventory)
    val library = Library(inventory, borrowManager)

    val book1 = Book("B1", "Clean Code", "Robert Martin")
    val book2 = Book("B2", "Kotlin in Action", "Isakova")
    val member = Member("M1", "Alice")

    library.addBook(book1)
    library.addBook(book2)

    library.borrowBook(member, "B1")
    library.borrowBook(member, "B2")

    println(library.getBorrowedBooks(member).map { it.name })
    // [Clean Code, Kotlin in Action]

    library.returnBook(member, "B1")
    println(library.getAvailableBooks().map { it.name })
    // [Clean Code]
}

data class Book(
    val id: String,
    val name: String,
    val author: String
)

data class Member(
    val id: String,
    val name: String
)

interface InventoryManager {
    fun addBook(book: Book)
    fun removeBook(bookId: String)
    fun getAvailableBooks(): List<Book>
    fun getBook(bookId: String): Book?
    fun quantity(bookId: String): Int
}

interface BorrowManager {
    fun borrowBook(member: Member, book: Book)
    fun returnBook(member: Member, book: Book)
    fun getBorrowedBooks(member: Member): List<Book>
}

class LibraryInventory : InventoryManager {
    private val books = HashMap<String, Pair<Book, Int>>() // id -> (book, quantity)

    override fun addBook(book: Book) {
        val (existingBook, count) = books[book.id] ?: Pair(book, 0)
        books[book.id] = Pair(existingBook, count + 1)
    }

    override fun removeBook(bookId: String) {
        val (book, count) = books[bookId]
            ?: throw IllegalArgumentException("Book $bookId not found")
        if (count <= 1) books.remove(bookId)
        else books[bookId] = Pair(book, count - 1)
    }

    override fun getAvailableBooks(): List<Book> {
        return books.values.filter { it.second > 0 }.map { it.first }
    }

    override fun getBook(bookId: String): Book? {
        return books[bookId]?.first
    }

    override fun quantity(bookId: String): Int {
        return books[bookId]?.second ?: 0
    }
}

class LibraryBorrowManager(
    private val borrowLimit: Int = 3,
    private val inventory: InventoryManager // DIP - depend on abstraction
) : BorrowManager {
    private val borrowedBooks = HashMap<String, MutableList<Book>>() // memberId -> books

    override fun borrowBook(member: Member, book: Book) {
        val currentBooks = borrowedBooks[member.id] ?: mutableListOf()
        if (currentBooks.size >= borrowLimit)
            throw IllegalStateException("Member ${member.name} has reached the borrow limit of $borrowLimit")
        if (inventory.quantity(book.id) == 0)
            throw IllegalStateException("No copies available for '${book.name}'")
        inventory.removeBook(book.id)
        currentBooks.add(book)
        borrowedBooks[member.id] = currentBooks
    }

    override fun returnBook(member: Member, book: Book) {
        val currentBooks = borrowedBooks[member.id]
            ?: throw IllegalStateException("No records found for member ${member.name}")
        if (!currentBooks.contains(book))
            throw IllegalStateException("'${book.name}' was not borrowed by ${member.name}")
        currentBooks.remove(book)
        inventory.addBook(book)
    }

    override fun getBorrowedBooks(member: Member): List<Book> {
        return borrowedBooks[member.id] ?: emptyList()
    }
}

class Library(
    private val inventory: InventoryManager,
    private val borrowManager: BorrowManager
) {
    fun addBook(book: Book) = inventory.addBook(book)

    fun getAvailableBooks(): List<Book> = inventory.getAvailableBooks()

    fun borrowBook(member: Member, bookId: String) {
        val book = inventory.getBook(bookId)
            ?: throw IllegalArgumentException("Book $bookId not found")
        borrowManager.borrowBook(member, book)
        println("${member.name} borrowed '${book.name}'")
    }

    fun returnBook(member: Member, bookId: String) {
        val book = inventory.getBook(bookId)
            ?: throw IllegalArgumentException("Book $bookId not found")
        borrowManager.returnBook(member, book)
        println("${member.name} returned '${book.name}'")
    }

    fun getBorrowedBooks(member: Member): List<Book> {
        return borrowManager.getBorrowedBooks(member)
    }
}
