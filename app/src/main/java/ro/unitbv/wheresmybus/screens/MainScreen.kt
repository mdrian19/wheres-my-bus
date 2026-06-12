package ro.unitbv.wheresmybus.screens

import android.R.attr.bottom
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
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
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(brasov, 14f)
    }

    var searchQuery by remember { mutableStateOf("") }
    var busStops by remember { mutableStateOf<List<BusStop>>(emptyList()) }

    var selectedStop by remember { mutableStateOf<BusStop?>(null) }
    var favoriteStops by remember { mutableStateOf<List<String>>(emptyList()) }
    val db = Firebase.firestore
    val currentUser = Firebase.auth.currentUser

    LaunchedEffect(Unit) {
        val db = Firebase.firestore
        db.collection("bus_stops")
            .get()
            .addOnSuccessListener { result ->
                val fetchedStops = mutableListOf<BusStop>()
                for (document in result) {
                    val name = document.getString("name") ?: ""
                    val lat = document.getDouble("lat") ?: 0.0
                    val lng = document.getDouble("lng") ?: 0.0
                    val snippet = document.getString("snippet") ?: ""
                    val line = document.getString("line") ?: ""
                    fetchedStops.add(BusStop(name, LatLng(lat, lng), snippet, line))
                }
                busStops = fetchedStops
            }
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        val favorites = snapshot.get("favourites") as? List<String> ?: emptyList()
                        favoriteStops = favorites
                    }
                }
        }
    }

    val filteredStops = busStops.filter { stop ->
        searchQuery.isBlank() || stop.line.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stops Map") },
                actions = {
                    IconButton(
                        onClick = {
                            navController.navigate(Screen.Favorites.route)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorites"
                        )
                    }
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                userManager.setLoggedIn(false)
                                Firebase.auth.signOut()
                                navController.navigate(Screen.Guest.route) {
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
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                contentPadding = PaddingValues(bottom = if(selectedStop != null) 140.dp else 16.dp),
                onMapClick = { selectedStop = null }
            ) {
                filteredStops.forEach { stop ->
                    Marker(
                        state = MarkerState(position = stop.location),
                        title = "${stop.name} (Line ${stop.line})",
                        onClick = {
                            selectedStop = stop
                            false
                        }
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

            if(selectedStop != null){
                val stop = selectedStop!!
                val isFavorite = favoriteStops.contains(stop.name)

                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ){
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ){
                        Column(modifier = Modifier.weight(1f)){
                            Text(text = stop.name, style = MaterialTheme.typography.titleLarge)
                            Text(text = "Line ${stop.line}: ${stop.snippet}", style = MaterialTheme.typography.bodyMedium)
                        }

                        IconButton(
                            onClick = {
                                if(currentUser != null){
                                    val userRef = db.collection("users").document(currentUser.uid)
                                    if(isFavorite){
                                        userRef.set(
                                            hashMapOf("favorites" to FieldValue.arrayRemove(stop.name)),
                                            SetOptions.merge()
                                        )
                                    } else {
                                        userRef.set(
                                            hashMapOf("favorites" to FieldValue.arrayUnion(stop.name)),
                                            SetOptions.merge()
                                        )
                                    }
                                }
                            }
                        ){
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color(0xFFFFD700) else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(onClick = { selectedStop = null }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }
            }
        }
    }
}