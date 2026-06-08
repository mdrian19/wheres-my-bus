package ro.unitbv.wheresmybus.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import ro.unitbv.wheresmybus.data.UserManager
import ro.unitbv.wheresmybus.models.Screen
import androidx.compose.ui.graphics.Color


data class BusStop(
    val name: String,
    val location: LatLng,
    val snippet: String,
    val line: String
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController) {
    val context = LocalContext.current
    val userManager = remember { UserManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val brasov = LatLng(45.657974, 25.601198)
    val cameraPositionState = rememberCameraPositionState{
        position = CameraPosition.fromLatLngZoom(brasov, 14f)
    }

    var searchQuery by remember { mutableStateOf("")}

    val allStops = remember{
        listOf(
            BusStop("Livada Poștei", LatLng(45.647185, 25.589823), "Cap de linie - Centru", "36"),
            BusStop("Dramatic", LatLng(45.645163, 25.594770), "Teatrul Sică Alexandrescu", "36"),
            BusStop("Castanilor", LatLng(45.648215, 25.602143), "Bulevardul 15 Noiembrie", "36"),
            BusStop("Onix", LatLng(45.651755, 25.603300), "Bulevardul Griviței", "36"),
            BusStop("Mircea cel Bătrân", LatLng(45.655866, 25.607415), "Bulevardul Griviței", "36"),
            BusStop("Făget", LatLng(45.658252, 25.610534), "Griviței - Intersecție 13 Decembrie", "36"),
            BusStop("Spital Tractorul", LatLng(45.662058, 25.612268), "Strada 13 Decembrie", "36"),
            BusStop("Piața Tractorul", LatLng(45.666115, 25.611151), "Piața Agroalimentară", "36"),
            BusStop("Bronzului", LatLng(45.670550, 25.612800), "Cartier Tractorul Nou", "36"),
            BusStop("Ioan Socec", LatLng(45.671800, 25.611000), "Intersecție Socec / 1 Decembrie", "36"),
            BusStop("Ștefan Baciu", LatLng(45.673100, 25.609500), "Extindere Cartier Tractorul", "36"),
            BusStop("Alexandru Ciurcu", LatLng(45.673900, 25.607200), "Extindere Cartier Tractorul", "36"),
            BusStop("Argintului", LatLng(45.672000, 25.604500), "Strada Argintului", "36"),
            BusStop("Independenței", LatLng(45.669520, 25.605230), "Cap de linie - Cartier Tractorul", "36"),

            BusStop("Gara Brașov", LatLng(45.6624, 25.6133), "Cap de linie - Gară", "4"),
            BusStop("Piața Sfatului", LatLng(45.6427, 25.5887), "Centrul istoric", "4"),
            BusStop("Pe Tocile", LatLng(45.6342, 25.5811), "Cartier Șchei", "4")
        )
    }

    val filteredStops = allStops.filter{ stop ->
        searchQuery.isBlank() || stop.line.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Where's My Bus?") },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch{
                                userManager.setLoggedIn(false)
                                navController.navigate(Screen.Guest.route){
                                    popUpTo(Screen.Main.route) { inclusive = true }
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ){
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ){
                filteredStops.forEach{stop ->
                    Marker(
                        state = MarkerState(position = stop.location),
                        title = stop.name,
                        snippet = stop.snippet
                    )
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search line(e.g. 36, 4)") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search icon")
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            )
        }
    }
}