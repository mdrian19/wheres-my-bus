package ro.unitbv.wheresmybus.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Camera
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.firebase.Firebase
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm
import com.google.maps.android.clustering.algo.PreCachingAlgorithmDecorator
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.unitbv.wheresmybus.models.Screen

@Composable
fun GuestScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = Firebase.firestore

    val brasov = LatLng(45.657974, 25.601198)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(brasov, 13f)
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var busStops by remember{ mutableStateOf<List<BusStop>>(emptyList()) }
    var mapProperties by remember { mutableStateOf(MapProperties(isMyLocationEnabled = false)) }
    var clusterManager by remember { mutableStateOf<ClusterManager<BusStop>?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if(isGranted){
            mapProperties = MapProperties(isMyLocationEnabled = true)
        }
    }

    LaunchedEffect(Unit) {
        val permissionCheckResult = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
            mapProperties = MapProperties(isMyLocationEnabled = true)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(Unit) {
        db.collection("bus_stops")
            .get(Source.SERVER)
            .addOnSuccessListener { result ->
                coroutineScope.launch(Dispatchers.IO) {
                    val finalStops = if (result.isEmpty) {
                        seedDatabaseFromJson(context, "stops.json")
                    } else {
                        val fetchedStops = mutableListOf<BusStop>()
                        for (document in result) {
                            val id = document.getLong("id") ?: 0L
                            val name = document.getString("name") ?: "Unknown"
                            val lat = document.getDouble("lat") ?: 0.0
                            val lng = document.getDouble("lng") ?: 0.0
                            val lines = document.get("lines") as? List<String> ?: emptyList()
                            fetchedStops.add(
                                BusStop(
                                    id = id, name = name, location = LatLng(lat, lng), lines = lines,
                                    hasBench = false, hasShelter = false, hasRealTimeBoard = false,
                                    wheelchairAccess = "unknown", operator = "RAT Brașov"
                                )
                            )
                        }
                        fetchedStops
                    }
                    withContext(Dispatchers.Main) {
                        busStops = finalStops.toList()
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.e("Firebase", "Error at loading stops for Guest user: ", error)
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            contentPadding = PaddingValues(bottom = navBarPadding + 80.dp),
            properties = mapProperties,
            uiSettings = MapUiSettings(myLocationButtonEnabled = true)
        ) {
            MapEffect(Unit){ map ->
                if(clusterManager == null){
                    val manager = ClusterManager<BusStop>(context, map)
                    val algorithm = NonHierarchicalDistanceBasedAlgorithm<BusStop>()
                    manager.algorithm = PreCachingAlgorithmDecorator(algorithm)
                    val renderer = DefaultClusterRenderer<BusStop>(context, map, manager)
                    renderer.minClusterSize = 4
                    manager.renderer = renderer
                    map.setOnCameraIdleListener(manager)
                    map.setOnMarkerClickListener(manager)
                    manager.setOnClusterItemClickListener{
                        navController.navigate(Screen.Login.route)
                        true
                    }
                    clusterManager = manager
                }
            }

            LaunchedEffect(busStops, clusterManager){
                clusterManager?.let{ manager ->
                    manager.clearItems()
                    manager.addItems(busStops)
                    manager.cluster()
                }
            }
        }

        Button(
            onClick = {
                navController.navigate(Screen.Login.route)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            Text("Sign in for complete access")
        }
    }
}