package ro.unitbv.wheresmybus.network
import retrofit2.http.GET

interface BusApiService {
    @GET("bus-schedule")
    suspend fun getSchedule(): List<BusSchedule>

    @GET("traffic-alerts")
    suspend fun getAlerts(): List<TrafficAlert>
}