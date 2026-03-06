package atmMachine

fun main() {
    val repository = BankAccountRepositoryImpl()
    val card = Card("1234-5678", pin = 1234, bankAccountId = "ACC1")
    val account = BankAccount("ACC1", balance = 1000)
    repository.registerCard(card, account)

    val atm = ATMSystem(
        authenticationManager = AuthenticationManagerImpl(),
        transactionManager = TransactionManagerImpl(repository),
        bankAccountRepository = repository
    )

    atm.insertCard(card)
    atm.authenticate(1234)
    atm.checkBalance()        // Balance: 1000
    atm.deposit(500)          // Deposited 500. New balance: 1500
    atm.withdraw(200)         // Withdrew 200. New balance: 1300
    atm.withdraw(2000)        // Insufficient funds
    atm.endSession()
}


data class Card(val number: String, val pin: Int, val bankAccountId: String)
data class BankAccount(val id: String, var balance: Int)

interface BankAccountRepository {
    fun findByCardNumber(cardNumber: String): BankAccount
    fun save(account: BankAccount)
}

interface AuthenticationManager {
    fun authenticate(card: Card, pin: Int): Boolean
    fun endSession()
}

interface TransactionManager {
    fun withdraw(account: BankAccount, amount: Int): Boolean
    fun deposit(account: BankAccount, amount: Int)
    fun getBalance(account: BankAccount): Int
}

class BankAccountRepositoryImpl : BankAccountRepository {
    private val accounts = mutableMapOf<String, BankAccount>()
    private val cardToAccount = mutableMapOf<String, String>()

    fun registerCard(card: Card, account: BankAccount) {
        accounts[account.id] = account
        cardToAccount[card.number] = account.id
    }

    override fun findByCardNumber(cardNumber: String): BankAccount {
        val accountId = cardToAccount[cardNumber]
            ?: throw IllegalArgumentException("Card $cardNumber not registered")
        return accounts[accountId]
            ?: throw IllegalArgumentException("Account not found")
    }

    override fun save(account: BankAccount) {
        accounts[account.id] = account
    }
}

class AuthenticationManagerImpl : AuthenticationManager {
    override fun authenticate(card: Card, pin: Int) = card.pin == pin
    override fun endSession() = println("Session ended")
}

class TransactionManagerImpl(
    private val repository: BankAccountRepository
) : TransactionManager {
    override fun withdraw(account: BankAccount, amount: Int): Boolean {
        if (account.balance < amount) return false
        account.balance -= amount
        repository.save(account)
        return true
    }

    override fun deposit(account: BankAccount, amount: Int) {
        account.balance += amount
        repository.save(account)
    }

    override fun getBalance(account: BankAccount) = account.balance
}

class ATMSystem(
    private val authenticationManager: AuthenticationManager,
    private val transactionManager: TransactionManager,
    private val bankAccountRepository: BankAccountRepository
) {
    sealed class ATMState {
        data object Idle : ATMState()
        data object ReadyForTransaction : ATMState()
        data object TransactionInProgress : ATMState()
    }

    private var atmState: ATMState = ATMState.Idle
    private var currentCard: Card? = null

    fun insertCard(card: Card) {
        if (atmState != ATMState.Idle)
            throw IllegalStateException("ATM is busy")
        currentCard = card
        println("Card inserted: ${card.number}")
    }

    fun authenticate(pin: Int) {
        val card = currentCard
            ?: throw IllegalStateException("No card inserted")
        val isAuthenticated = authenticationManager.authenticate(card, pin)
        atmState = if (isAuthenticated) {
            println("Authentication successful")
            ATMState.ReadyForTransaction
        } else {
            println("Wrong PIN")
            ATMState.Idle
        }
    }

    fun withdraw(amount: Int) {
        val account = getAuthenticatedAccount()
        atmState = ATMState.TransactionInProgress
        val success = transactionManager.withdraw(account, amount)
        atmState = ATMState.ReadyForTransaction
        if (success) println("Withdrew $amount. New balance: ${account.balance}")
        else println("Insufficient funds")
    }

    fun deposit(amount: Int) {
        val account = getAuthenticatedAccount()
        atmState = ATMState.TransactionInProgress
        transactionManager.deposit(account, amount)
        atmState = ATMState.ReadyForTransaction
        println("Deposited $amount. New balance: ${account.balance}")
    }

    fun checkBalance() {
        val account = getAuthenticatedAccount()
        println("Balance: ${transactionManager.getBalance(account)}")
    }

    fun endSession() {
        currentCard = null
        atmState = ATMState.Idle
        authenticationManager.endSession()
    }

    private fun getAuthenticatedAccount(): BankAccount {
        if (atmState != ATMState.ReadyForTransaction)
            throw IllegalStateException("Not authenticated")
        val card = currentCard
            ?: throw IllegalStateException("No card inserted")
        return bankAccountRepository.findByCardNumber(card.number)
    }
}
