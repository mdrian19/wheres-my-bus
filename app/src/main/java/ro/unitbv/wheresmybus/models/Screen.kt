package ro.unitbv.wheresmybus.models

sealed class Screen (val route: String) {
    object Guest : Screen("guest_screen")
    object Login : Screen("login_screen")
    object Register : Screen("register_screen")
    object Main : Screen("main_screen")
    object Favorites: Screen("favorites_screen")
    object Alerts: Screen("alerts_screen")
    object Profile: Screen("profile_screen")
}