package restaurantSystem

fun main() {
    val menu = MenuImpl()
    val kitchen = KitchenImpl()
    val orderManager = OrderManagerImpl(kitchen)
    val restaurant = RestaurantSystem(menu, orderManager, kitchen)

    menu.addMenuItem(MenuItem(1, "Burger", "Beef burger", 9.99))

    val items = listOf(
        OrderItem(menu.getMenuItemById(1)!!, 2, listOf(Customization("no onions")))
    )

    // place two orders
    val order1 = restaurant.placeOrder(items)
    val order2 = restaurant.placeOrder(items)

    println(order1.status) // placed ✅
    println(order2.status) // placed ✅

    // cancel order2 while still placed - now possible!
    restaurant.cancelOrder(order2.id) // works! ✅

    // kitchen cooks remaining orders
    restaurant.cookOrders()

    println(order1.status) // ready ✅
    println(order2.status) // cancelled ✅
}

data class Customization(val name: String)

data class MenuItem(
    val id: Int,
    val name: String,
    val description: String,
    val price: Double
)

data class OrderItem(
    val menuItem: MenuItem,
    val quantity: Int,
    val customizations: List<Customization>
)

enum class OrderStatus {
    placed, preparing, ready, cancelled
}

data class Order(
    val id: Int,
    val items: List<OrderItem>,
    var status: OrderStatus
) {
    val totalPrice: Double
        get() = items.sumOf { it.menuItem.price * it.quantity }
}

interface Menu {
    fun getMenuItems(): List<MenuItem>
    fun getMenuItemById(id: Int): MenuItem?
    fun addMenuItem(item: MenuItem)
    fun removeMenuItem(id: Int)
}

interface OrderManager {
    fun placeOrder(items: List<OrderItem>): Order
    fun getOrderById(id: Int): Order?
    fun updateOrderStatus(orderId: Int, status: OrderStatus)
    fun getOrdersByStatus(status: OrderStatus): List<Order>
    fun cancel(orderId: Int)
}

interface Kitchen {
    fun receiveOrder(order: Order, onStatusUpdate: (Int, OrderStatus) -> Unit)
    fun cancel(orderId: Int)
    fun cook() // made public so it can be triggered
}

class MenuImpl : Menu {
    private val items = mutableMapOf<Int, MenuItem>()

    override fun getMenuItems() = items.values.toList()

    override fun getMenuItemById(id: Int) = items[id]

    override fun addMenuItem(item: MenuItem) {
        items[item.id] = item
    }

    override fun removeMenuItem(id: Int) {
        items.remove(id) ?: throw IllegalArgumentException("Item $id not found")
    }
}


class KitchenImpl : Kitchen {
    private val orders = mutableMapOf<Int, (Int, OrderStatus) -> Unit>()

    override fun receiveOrder(order: Order, onStatusUpdate: (Int, OrderStatus) -> Unit) {
        orders[order.id] = onStatusUpdate
        // removed the immediate preparing callback!
        println("Kitchen queued order ${order.id}")
    }

    override fun cook() {
        orders.toMap().forEach { (orderId, onStatusUpdate) ->
            onStatusUpdate(orderId, OrderStatus.preparing)
            println("Kitchen cooking order $orderId")
            // simulate cooking then mark ready
            onStatusUpdate(orderId, OrderStatus.ready)
            orders.remove(orderId)
            println("Kitchen finished order $orderId")
        }
    }

    override fun cancel(orderId: Int) {
        orders.remove(orderId)
            ?: throw IllegalArgumentException("Order $orderId not in kitchen queue")
        println("Kitchen removed order $orderId from queue")
    }
}

class OrderManagerImpl(
    private val kitchen: Kitchen
) : OrderManager {
    private val orders = mutableMapOf<Int, Order>() // use map for O(1) lookup
    private var orderCounter = 1

    override fun placeOrder(items: List<OrderItem>): Order {
        val order = Order(
            id = orderCounter++,
            items = items,
            status = OrderStatus.placed
        )
        orders[order.id] = order
        kitchen.receiveOrder(order) { orderId, status ->
            updateOrderStatus(orderId, status)
        }
        println("Order ${order.id} placed. Total: $${order.totalPrice}")
        return order
    }

    override fun getOrderById(id: Int): Order? = orders[id]

    override fun updateOrderStatus(orderId: Int, status: OrderStatus) {
        val order = orders[orderId]
            ?: throw IllegalArgumentException("Order $orderId not found")
        order.status = status
        println("Order $orderId status updated to $status")
        if (status == OrderStatus.cancelled) orders.remove(orderId)
    }

    override fun getOrdersByStatus(status: OrderStatus): List<Order> {
        return orders.values.filter { it.status == status }
    }

    override fun cancel(orderId: Int) {
        val order = orders[orderId]
            ?: throw IllegalArgumentException("Order $orderId not found")
        if (order.status != OrderStatus.placed)
            throw IllegalStateException("Cannot cancel order in state ${order.status}")
        kitchen.cancel(orderId)
    }
}

class RestaurantSystem(
    private val menu: Menu,
    private val orderManager: OrderManager,
    private val kitchen: Kitchen
) {
    fun getMenu() = menu.getMenuItems()

    fun placeOrder(items: List<OrderItem>) = orderManager.placeOrder(items)

    fun cancelOrder(orderId: Int) = orderManager.cancel(orderId)

    fun getOrder(orderId: Int) = orderManager.getOrderById(orderId)

    fun cookOrders() = kitchen.cook() // triggers kitchen to finish all orders
}
