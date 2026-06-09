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
import android.util.Log
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore


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

    var busStops by remember { mutableStateOf<List<BusStop>>(emptyList())}

    LaunchedEffect(Unit){
        val db = Firebase.firestore
        db.collection("bus_stops")
            .get()
            .addOnSuccessListener{ result ->
                val fetchedStops = mutableListOf<BusStop>()
                for(document in result) {
                    val name = document.getString("name") ?: ""
                    val lat = document.getDouble("lat") ?: 0.0
                    val lng = document.getDouble("lng") ?: 0.0
                    val snippet = document.getString("snippet") ?: ""
                    val line = document.getString("line") ?: ""

                    fetchedStops.add(BusStop(name, LatLng(lat, lng), snippet, line))
                }
                busStops = fetchedStops
            }
            .addOnFailureListener { exception ->
                Log.e("FirebaseError", "Error on stop read", exception)
            }
    }

    val filteredStops = busStops.filter{ stop ->
        searchQuery.isBlank() || stop.line.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stops Map") },
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
                        title = "${stop.name} (Line ${stop.line})",
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
                    .align(Alignment.TopCenter),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Go
                )
            )
        }
    }
}