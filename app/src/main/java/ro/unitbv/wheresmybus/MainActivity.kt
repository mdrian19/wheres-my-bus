package ro.unitbv.wheresmybus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ro.unitbv.wheresmybus.data.UserManager
import ro.unitbv.wheresmybus.models.Screen
import ro.unitbv.wheresmybus.screens.GuestScreen
import ro.unitbv.wheresmybus.screens.LoginScreen
import ro.unitbv.wheresmybus.screens.MainScreen
import ro.unitbv.wheresmybus.screens.RegisterScreen
import ro.unitbv.wheresmybus.ui.theme.WheresMyBusTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import ro.unitbv.wheresmybus.screens.AlertsScreen
import ro.unitbv.wheresmybus.screens.FavoritesScreen
import ro.unitbv.wheresmybus.screens.ProfileScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
           MaterialTheme {
               Surface(
                   modifier = Modifier.fillMaxSize(),
                   color = MaterialTheme.colorScheme.background
               ) {
                   AppNavigation()
               }
            }
        }
    }
}

@Composable
fun AppNavigation(){
    val context = LocalContext.current
    val userManager = remember { UserManager(context) }
    val isLoggedInState by userManager.isLoggedInFlow.collectAsState(initial = null)
    val isLoggedIn = isLoggedInState

    if(isLoggedIn == null){
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
            CircularProgressIndicator()
        }
    } else {
        val navController = rememberNavController()
        val startRoute = if(isLoggedIn) Screen.Main.route else Screen.Guest.route

        NavHost(navController = navController, startDestination = startRoute){
            composable(Screen.Guest.route){
                GuestScreen(navController = navController)
            }
            composable(Screen.Login.route){
                LoginScreen(navController = navController)
            }
            composable(Screen.Register.route){
                RegisterScreen(navController = navController)
            }
            composable(Screen.Main.route) {
                MainScreen(navController = navController)
            }
            composable(Screen.Favorites.route) {
                FavoritesScreen(navController = navController)
            }
            composable(Screen.Alerts.route){
                AlertsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(Screen.Profile.route){
                ProfileScreen(navController = navController)
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    WheresMyBusTheme {
        Greeting("Android")
    }
}