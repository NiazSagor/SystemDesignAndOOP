package parkinglot

import kotlin.reflect.KClass

fun main() {
    val spots = listOf(
        ParkingSpot.Compact("C1"),
        ParkingSpot.Regular("R1"),
        ParkingSpot.Large("L1")
    )

    val coordinator = ParkingCoordinatorImpl(spots)
    val feeCalculator = ParkingFeeCalculator()
    val sessionManager = ParkingSessionManagerImpl(feeCalculator)
    val system = ParkingSystem(coordinator, sessionManager)

    val car = Vehicle.Car("CAR1")
    val truck = Vehicle.Truck("TRUCK1")

    system.park(car)    // parks at R1
    system.park(truck)  // parks at L1

    system.checkOut(car)   // fee based on duration at Regular rate
    system.checkOut(truck) // fee based on duration at Large rate
}


sealed class ParkingSpot(val id: String) {
    class Compact(id: String) : ParkingSpot(id)
    class Regular(id: String) : ParkingSpot(id)
    class Large(id: String) : ParkingSpot(id)
}

data class ParkingSession(
    val vehicle: Vehicle,
    val spot: ParkingSpot,
    val entryTime: Long,
    val exitTime: Long? = null,  // null until session ends
    val fee: Int? = null
)

sealed class Vehicle(val id: String) {
    abstract val compatibleSpots: List<KClass<out ParkingSpot>>

    class Motorcycle(id: String) : Vehicle(id) {
        override val compatibleSpots = listOf(
            ParkingSpot.Compact::class,
            ParkingSpot.Regular::class,
            ParkingSpot.Large::class
        )
    }

    class Car(id: String) : Vehicle(id) {
        override val compatibleSpots = listOf(
            ParkingSpot.Regular::class,
            ParkingSpot.Large::class
        )
    }

    class Truck(id: String) : Vehicle(id) {
        override val compatibleSpots = listOf(ParkingSpot.Large::class)
    }
}

interface FeeCalculator {
    fun calculate(spot: ParkingSpot, durationMinutes: Long): Int
}

interface ParkingSessionManager {
    fun startSession(vehicle: Vehicle, parkingSpot: ParkingSpot)
    fun endSession(vehicle: Vehicle): ParkingSession
    fun getSession(vehicle: Vehicle): ParkingSession
}

interface ParkingCoordinator {
    fun park(vehicle: Vehicle): ParkingSpot?
    fun leave(spot: ParkingSpot)
    fun getAvailableSpots(vehicle: Vehicle): List<ParkingSpot>
}

class ParkingFeeCalculator : FeeCalculator {
    override fun calculate(spot: ParkingSpot, durationMinutes: Long): Int {
        val hours = maxOf(1, (durationMinutes / 60).toInt()) // minimum 1 hour charge
        return when (spot) {
            is ParkingSpot.Compact -> 10 * hours
            is ParkingSpot.Regular -> 20 * hours
            is ParkingSpot.Large -> 30 * hours
        }
    }
}

class ParkingSessionManagerImpl(
    private val feeCalculator: FeeCalculator
) : ParkingSessionManager {
    private val sessions = mutableMapOf<String, ParkingSession>() // vehicleId -> session

    override fun startSession(vehicle: Vehicle, spot: ParkingSpot) {
        val session = ParkingSession(vehicle, spot, System.currentTimeMillis())
        sessions[vehicle.id] = session
    }

    override fun endSession(vehicle: Vehicle): ParkingSession {
        val session = sessions[vehicle.id]
            ?: throw IllegalStateException("No session for ${vehicle::class.simpleName} ${vehicle.id}")
        val durationMinutes = (System.currentTimeMillis() - session.entryTime) / 60_000
        val fee = feeCalculator.calculate(session.spot, durationMinutes)
        sessions.remove(vehicle.id)
        return session.copy(exitTime = System.currentTimeMillis(), fee = fee)
    }

    override fun getSession(vehicle: Vehicle): ParkingSession {
        return sessions[vehicle.id]
            ?: throw IllegalStateException("No session for ${vehicle::class.simpleName} ${vehicle.id}")
    }
}

class ParkingCoordinatorImpl(
    private val allSpots: List<ParkingSpot>
) : ParkingCoordinator {

    private val occupiedSpots = mutableSetOf<ParkingSpot>()

    override fun park(vehicle: Vehicle): ParkingSpot? {
        val availableSpots = getAvailableSpots(vehicle)
        if (availableSpots.isEmpty()) return null
        val spot = availableSpots.first()
        occupiedSpots.add(spot)
        println("${vehicle::class.simpleName} ${vehicle.id} parked at ${spot::class.simpleName} ${spot.id}")
        return spot
    }

    override fun leave(spot: ParkingSpot) {
        if (!occupiedSpots.contains(spot))
            throw IllegalStateException("Spot ${spot.id} is not occupied")
        occupiedSpots.remove(spot)
        println("Spot ${spot::class.simpleName} ${spot.id} is now free")
    }

    override fun getAvailableSpots(vehicle: Vehicle): List<ParkingSpot> {
        return allSpots.filter { spot ->
            val isCompatible = spot::class in vehicle.compatibleSpots  // right type?
            val isFree = spot !in occupiedSpots                         // not occupied?
            isCompatible && isFree
        }
    }
}

class ParkingSystem(
    private val coordinator: ParkingCoordinator,
    private val sessionManager: ParkingSessionManager? = null // optional - OCP!
) {
    fun park(vehicle: Vehicle): ParkingSpot? {
        val spot = coordinator.park(vehicle) ?: return null
        sessionManager?.startSession(vehicle, spot)
        return spot
    }

    fun checkOut(vehicle: Vehicle): ParkingSession {
        val session = sessionManager?.endSession(vehicle)
            ?: throw IllegalStateException("No session manager configured")
        coordinator.leave(session.spot)
        println(
            "${vehicle.id} checked out. Fee: ${session.fee} cents. Duration: ${
                (session.exitTime!! - session.entryTime) / 60_000
            } minutes"
        )
        return session
    }

    fun leave(spot: ParkingSpot) = coordinator.leave(spot) // still works without fees!

    fun getAvailableSpots(vehicle: Vehicle) = coordinator.getAvailableSpots(vehicle)
}