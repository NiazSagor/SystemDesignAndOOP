package parkinglot

import kotlin.reflect.KClass

fun main() {
    val spots = listOf(
        ParkingSpot.Compact("C1"),
        ParkingSpot.Compact("C2"),
        ParkingSpot.Regular("R1"),
        ParkingSpot.Regular("R2"),
        ParkingSpot.Large("L1")
    )

    val coordinator = ParkingCoordinatorImpl(spots)
    val system = ParkingSystem(coordinator)

    val motorcycle = Vehicle.Motorcycle("M1")
    val car = Vehicle.Car("CAR1")
    val truck = Vehicle.Truck("T1")

    val motoSpot = system.park(motorcycle)  // parks in C1 (first compatible)
    val carSpot = system.park(car)          // parks in R1 (can't use compact)
    system.park(truck)                      // parks in L1 (large only)

    motoSpot?.let { system.leave(it) }      // C1 is free again
    system.park(car)                        // now parks in R2
}


sealed class ParkingSpot(val id: String) {
    class Compact(id: String) : ParkingSpot(id)
    class Regular(id: String) : ParkingSpot(id)
    class Large(id: String) : ParkingSpot(id)
}

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

interface ParkingCoordinator {
    fun park(vehicle: Vehicle): ParkingSpot?
    fun leave(spot: ParkingSpot)
    fun getAvailableSpots(vehicle: Vehicle): List<ParkingSpot>
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

class ParkingSystem(private val coordinator: ParkingCoordinator) {

    fun park(vehicle: Vehicle): ParkingSpot? {
        val spot = coordinator.park(vehicle)
        if (spot == null) println("No available spot for ${vehicle::class.simpleName} ${vehicle.id}")
        return spot
    }

    fun leave(spot: ParkingSpot) = coordinator.leave(spot)

    fun getAvailableSpots(vehicle: Vehicle) = coordinator.getAvailableSpots(vehicle)
}
