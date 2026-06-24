package ro.unitbv.wheresmybus.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserData(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val email: String
)

@JsonClass(generateAdapter = true)
data class BusSchedule(
    val line: String,
    val eta: Int,
    val lineEnd: String
)

enum class AlertGrade{
    Low,
    Medium,
    High,
    VeryHigh
}
@JsonClass(generateAdapter = true)
data class TrafficAlert(
    val id: Int,
    val title: String,
    val text: String,
    val grade: AlertGrade
)