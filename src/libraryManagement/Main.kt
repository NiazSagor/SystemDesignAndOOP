fun main() {
    val inventory = LibraryInventory()
    val borrowManager = LibraryBorrowManager(borrowLimit = 3, inventory = inventory)
    val reservationManager = ReservationManagerImpl()
    val notifier = ConsoleReservationNotifier()
    val library = Library(inventory, borrowManager, reservationManager, notifier)

    val book = Book("B1", "Clean Code", "Robert Martin")
    val alice = Member("M1", "Alice")
    val bob = Member("M2", "Bob")
    val charlie = Member("M3", "Charlie")

    library.addBook(book) // 1 copy

    library.borrowBook(alice, "B1")   // Alice borrows it
    library.reserve(bob, "B1")        // Bob joins waitlist (position 1)
    library.reserve(charlie, "B1")    // Charlie joins waitlist (position 2)

    library.returnBook(alice, "B1")
    // Bob gets auto-assigned + notified
    // Charlie remains on waitlist

    library.returnBook(bob, "B1")
    // Charlie gets auto-assigned + notified
}

data class Reservation(
    val member: Member,
    val book: Book,
    val reservedAt: Long = System.currentTimeMillis()
)

interface ReservationManager {
    fun reserve(member: Member, book: Book)
    fun cancel(member: Member, book: Book)
    fun getNextReservation(bookId: String): Reservation?
    fun hasReservation(bookId: String): Boolean
}

interface ReservationNotifier {
    fun notify(member: Member, book: Book)
}

class ConsoleReservationNotifier : ReservationNotifier {
    override fun notify(member: Member, book: Book) {
        println("📚 Hey ${member.name}! '${book.name}' is now available for you.")
    }
}

class ReservationManagerImpl(
) : ReservationManager {
    // bookId -> queue of reservations
    private val reservations = mutableMapOf<String, ArrayDeque<Reservation>>()

    override fun reserve(member: Member, book: Book) {
        val queue = reservations.getOrPut(book.id) { ArrayDeque() }
        if (queue.any { it.member == member })
            throw IllegalStateException("${member.name} already reserved '${book.name}'")
        queue.addLast(Reservation(member, book))
        println("${member.name} added to waitlist for '${book.name}' (position ${queue.size})")
    }

    override fun cancel(member: Member, book: Book) {
        reservations[book.id]?.removeIf { it.member == member }
            ?: throw IllegalStateException("No reservations found for '${book.name}'")
        println("${member.name}'s reservation for '${book.name}' cancelled")
    }

    override fun getNextReservation(bookId: String): Reservation? {
        return reservations[bookId]?.firstOrNull()
    }

    override fun hasReservation(bookId: String): Boolean {
        return reservations[bookId]?.isNotEmpty() ?: false
    }
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
    private val borrowManager: BorrowManager,
    private val reservationManager: ReservationManager? = null,
    private val notifier: ReservationNotifier? = null
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

        // check if anyone is waiting
        if (reservationManager?.hasReservation(bookId) == true) {
            val reservation = reservationManager.getNextReservation(bookId)
            if (reservation != null) {
                borrowManager.borrowBook(reservation.member, book)
                reservationManager.cancel(reservation.member, book)
                notifier?.notify(reservation.member, book)
                println("'${book.name}' auto-assigned to ${reservation.member.name}")
            }
        }
    }

    fun getBorrowedBooks(member: Member): List<Book> {
        return borrowManager.getBorrowedBooks(member)
    }

    fun reserve(member: Member, bookId: String) {
        val book = inventory.getBook(bookId)
            ?: throw IllegalArgumentException("Book $bookId not found")
        if (inventory.quantity(bookId) > 0)
            throw IllegalArgumentException("'${book.name}' is available — just borrow it!")
        reservationManager?.reserve(member, book)
            ?: throw IllegalStateException("Reservation system not configured")
    }

    fun cancelReservation(member: Member, bookId: String) {
        val book = inventory.getBook(bookId)
            ?: throw IllegalArgumentException("Book $bookId not found")
        reservationManager?.cancel(member, book)
            ?: throw IllegalStateException("Reservation system not configured")
    }
}
