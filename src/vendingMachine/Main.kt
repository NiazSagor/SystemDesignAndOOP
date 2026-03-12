package vendingMachine

fun main() {
    val inventory = VendingMachineInventory()
    val transaction = VendingMachineTransaction()
    val discountManager = DiscountManagerImpl()

    discountManager.addDiscountCode(
        DiscountCode("SAVE10", DiscountType.Percentage(10), minimumSpend = 100)
    )
    discountManager.addDiscountCode(
        DiscountCode("FLAT20", DiscountType.Flat(20), minimumSpend = 50)
    )

    val machine = VendingMachine(inventory, transaction, discountManager)
    inventory.addProduct(Product("A1", "Coke", 150, 5))

    machine.selectProduct("A1")
    machine.applyDiscountCode("SAVE10") // 10% off 150 = 135
    machine.insertMoney(200)
    val change = machine.dispense() // change = 65 cents
}

data class Product(
    val id: String,
    val name: String,
    val price: Int,
    var quantity: Int
)

sealed class DiscountType {
    data class Percentage(val percent: Int) : DiscountType()
    data class Flat(val amount: Int) : DiscountType()
}

data class DiscountCode(
    val code: String,
    val type: DiscountType,
    val minimumSpend: Int
)

interface DiscountManager {
    fun validate(code: String): DiscountCode?
    fun apply(discountCode: DiscountCode, price: Int): Int
    fun addDiscountCode(discountCode: DiscountCode)
}

interface InventoryManager {
    fun addProduct(product: Product)
    fun isAvailable(productId: String): Boolean
    fun dispense(productId: String)
    fun getProduct(productId: String): Product?
}

interface TransactionProcessor {
    fun selectProduct(product: Product)
    fun insertMoney(amount: Int)
    fun getInsertedAmount(): Int
    fun calculateChange(): Int
    fun reset()
}

class DiscountManagerImpl : DiscountManager {
    private val codes = mutableMapOf<String, DiscountCode>()

    override fun addDiscountCode(discountCode: DiscountCode) {
        codes[discountCode.code] = discountCode
    }

    override fun validate(code: String) = codes[code]

    override fun apply(discountCode: DiscountCode, price: Int): Int {
        if (discountCode.minimumSpend > price) return price
        val discountedAmount = when (val type = discountCode.type) {
            is DiscountType.Flat -> type.amount
            is DiscountType.Percentage -> (price * type.percent) / 100
        }
        return price - discountedAmount
    }
}

class VendingMachineInventory : InventoryManager {
    private val products = mutableMapOf<String, Product>()

    override fun addProduct(product: Product) {
        products[product.id] = product
    }

    override fun isAvailable(productId: String): Boolean {
        return products[productId]?.quantity ?: 0 > 0
    }

    override fun dispense(productId: String) {
        val product = products[productId]
            ?: throw IllegalArgumentException("Product $productId not found")
        if (product.quantity <= 0)
            throw IllegalStateException("Product $productId is out of stock")
        product.quantity--
    }

    override fun getProduct(productId: String): Product? {
        return products[productId]
    }
}

class VendingMachineTransaction : TransactionProcessor {
    private var selectedProduct: Product? = null
    private var insertedAmount: Int = 0

    override fun selectProduct(product: Product) {
        selectedProduct = product
    }

    override fun insertMoney(amount: Int) {
        insertedAmount += amount
    }

    override fun getInsertedAmount() = insertedAmount

    override fun calculateChange(): Int {
        val product = selectedProduct
            ?: throw IllegalStateException("No product selected")
        if (insertedAmount < product.price)
            throw IllegalStateException("Insufficient funds")
        return insertedAmount - product.price
    }

    override fun reset() {
        selectedProduct = null
        insertedAmount = 0
    }
}

class VendingMachine(
    private val inventory: InventoryManager,
    private val transaction: TransactionProcessor,
    private val discountManager: DiscountManager? = null
) {
    private var currentProduct: Product? = null
    private var currentDiscount: DiscountCode? = null

    fun selectProduct(productId: String) {
        val product = inventory.getProduct(productId)
            ?: throw IllegalArgumentException("Product not found")
        if (!inventory.isAvailable(productId))
            throw IllegalStateException("Product is out of stock")
        currentProduct = product
        transaction.selectProduct(product)
        println("Selected: ${product.name} - ${product.price} cents")
    }

    fun applyDiscountCode(code: String) {
        val discount = discountManager?.validate(code)
        if (discount != null) {
            currentDiscount = discount
            println("Discount applied: ${discount.code}")
        } else {
            println("Invalid discount code")
        }
    }

    fun insertMoney(amount: Int) {
        transaction.insertMoney(amount)
        println("Inserted: $amount cents")
    }

    fun dispense(): Int {
        val product = currentProduct
            ?: throw IllegalStateException("No product selected")

        val finalPrice = currentDiscount?.let {
            discountManager!!.apply(it, product.price)
        } ?: product.price

        val insertedAmount = transaction.getInsertedAmount()
        if (insertedAmount < finalPrice)
            throw IllegalStateException("Insufficient funds")

        val change = insertedAmount - finalPrice
        inventory.dispense(product.id)
        transaction.reset()
        currentProduct = null
        currentDiscount = null
        println("Dispensing ${product.name}. Change: $change cents")
        return change
    }

    fun cancel() {
        transaction.reset()
        currentProduct = null
        currentDiscount = null
        println("Transaction cancelled")
    }
}
