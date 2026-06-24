package ro.unitbv.wheresmybus.screens

import android.R.attr.onClick
import android.content.Context
import android.graphics.Color.parseColor
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BusAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ro.unitbv.wheresmybus.data.UserManager
import ro.unitbv.wheresmybus.models.Screen
import ro.unitbv.wheresmybus.data.DatabaseProvider
import androidx.compose.runtime.Immutable
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm
import com.google.maps.android.clustering.algo.PreCachingAlgorithmDecorator
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.Polyline
import androidx.compose.runtime.collectAsState
import ro.unitbv.wheresmybus.network.RetrofitClient
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import com.google.android.gms.maps.CameraUpdateFactory
import java.text.Normalizer
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings

@Immutable
data class BusStop(
    val id: Long,
    val name: String,
    val location: LatLng,
    val lines: List<String> = emptyList(),
    val hasBench: Boolean,
    val hasShelter: Boolean,
    val hasRealTimeBoard: Boolean,
    val wheelchairAccess: String,
    val operator: String
) : ClusterItem {
    override fun getPosition(): LatLng = location
    override fun getTitle(): String = name
    override fun getSnippet(): String = "Lines: ${lines.joinToString(", ")}"
    override fun getZIndex(): Float? = null

    override fun equals(other: Any?): Boolean {
        if (this === other)
            return true
        if (other !is BusStop)
            return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

@Immutable
data class BusRoute(
    val lineName: String,
    var color: Color,
    val points: List<LatLng>,
    val stops: List<BusStop> = emptyList()
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

    val filterOptions = listOf("All", "Lines", "Stops")
    var selectedFilter by remember { mutableStateOf(filterOptions[0]) }
    var expandedFilterMenu by remember { mutableStateOf(false) }

    var busStops by remember { mutableStateOf<List<BusStop>>(emptyList()) }
    var busRoutes by remember { mutableStateOf<List<BusRoute>>(emptyList()) }

    val currentUser = Firebase.auth.currentUser
    var selectedStop by remember { mutableStateOf<BusStop?>(null) }
    val favoriteDao = remember { DatabaseProvider.getDatabase(context).favoriteDao() }
    val favoriteStops by
    favoriteDao.getFavoritesFlow(currentUser?.uid ?: "").collectAsState(initial = emptyList())
    val db = Firebase.firestore
    var scheduleResponse by remember { mutableStateOf("Loading...") }
    var isUrgent by remember { mutableStateOf(false) }

    var mapProperties by remember { mutableStateOf(MapProperties(isMyLocationEnabled = false)) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            mapProperties = MapProperties(isMyLocationEnabled = true)
        }
    }

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val favoriteToSelect by savedStateHandle?.getStateFlow<String?>("selected_favorite", null)
        ?.collectAsState() ?: remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        val permissionCheckResult = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if(permissionCheckResult == PackageManager.PERMISSION_GRANTED){
            mapProperties = MapProperties(isMyLocationEnabled = true)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(favoriteToSelect, busStops) {
        if (favoriteToSelect != null && busStops.isNotEmpty()) {
            val stop = busStops.find { it.name == favoriteToSelect }
            if (stop != null) {
                selectedStop = stop
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(stop.location, 16f), 1000
                )
            }
            savedStateHandle?.remove<String>("selected_favorite")
        }
    }

    LaunchedEffect(selectedStop) {
        if (selectedStop != null) {
            while (true) {
                try {
                    val data = RetrofitClient.instance.getSchedule()
                    val sortedData = data.sortedBy { it.eta }
                    val fastestBus = sortedData.firstOrNull()

                    if (fastestBus != null) {
                        isUrgent = fastestBus.eta <= 2
                        scheduleResponse = "Line ${fastestBus.line}: ${fastestBus.eta} min"
                    } else {
                        scheduleResponse = "No available buses"
                    }
                } catch (e: Exception) {
                    Log.e("API_ERROR", "Error on parsing: ", e)
                    scheduleResponse = "Schedule unavaiable"
                    isUrgent = false
                }
                kotlinx.coroutines.delay(30000)
            }
        } else {
            scheduleResponse = "Loading..."
        }
    }

    LaunchedEffect(Unit) {
        db.collection("bus_stops")
            .get(Source.SERVER)
            .addOnSuccessListener { result ->
                coroutineScope.launch(Dispatchers.IO) {
                    val fetchedStops = if (result.isEmpty) {
                        Log.d("FirebaseInit", "Database is empty. Starting seeding...")
                        seedDatabaseFromJson(context, "stops.json")
                    } else {
                        result.map { document ->
                            BusStop(
                                id = document.getLong("id") ?: 0L,
                                name = document.getString("name") ?: "Unknown",
                                location = LatLng(document.getDouble("lat") ?: 0.0, document.getDouble("lng") ?: 0.0),
                                lines = (document.get("lines") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                                hasBench = document.getBoolean("hasBench") ?: false,
                                hasShelter = document.getBoolean("hasShelter") ?: false,
                                hasRealTimeBoard = document.getBoolean("hasRealTimeBoard") ?: false,
                                wheelchairAccess = document.getString("wheelchairAccess") ?: "unknown",
                                operator = document.getString("operator") ?: "RAT Brașov"
                            )
                        }
                    }

                    val loadedRoutes = loadRoutesFromJson(context, "lines.json", fetchedStops)

                    val updatedStops = fetchedStops.map { stop ->
                        val linesPassingStop = loadedRoutes
                            .filter { route -> route.stops.any { it.id == stop.id } }
                            .map { it.lineName }
                        val allLinesForStop = (stop.lines + linesPassingStop).distinct().sorted()
                        stop.copy(lines = allLinesForStop)
                    }

                    withContext(Dispatchers.Main) {
                        busStops = updatedStops
                        busRoutes = loadedRoutes.map { route ->
                            route.copy(stops = route.stops.mapNotNull { oldStop ->
                                updatedStops.find { it.id == oldStop.id }
                            })
                        }
                        Log.d("Routes", "Loaded ${busRoutes.size} routes.")
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.e("Firebase", "Error at rendering stops: ", error)
            }

        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        val cloudFavorites =
                            snapshot.get("favorites") as? List<String> ?: emptyList()
                        coroutineScope.launch(Dispatchers.IO) {
                            val entities = cloudFavorites.map {
                                ro.unitbv.wheresmybus.data.FavoriteEntity(
                                    currentUser.uid,
                                    it
                                )
                            }
                            favoriteDao.clearUserFavorites(currentUser.uid)
                            favoriteDao.insertAll(entities)
                        }
                    }
                }
        }
    }

    val activeRoutes by remember {
        derivedStateOf {
            val rawQuery = searchQuery.trim()
            val query = rawQuery.removeDiacritics()
            if (query.isBlank()) {
                emptyList()
            } else {
                if (selectedFilter == "Stops") {
                    emptyList()
                } else {
                    busRoutes.filter { it.lineName.removeDiacritics().equals(query, ignoreCase = true) }
                }
            }
        }
    }

    val filteredStops by remember {
        derivedStateOf {
            val rawQuery = searchQuery.trim()
            val query = rawQuery.removeDiacritics()
            if (query.isBlank()) {
                busStops
            } else {
                if (selectedFilter == "Lines") {
                    activeRoutes.flatMap { it.stops }.toSet().toList()
                } else {
                    val routeStops = activeRoutes.flatMap { it.stops }.toSet()
                    busStops.filter { stop ->
                        val matchesName = stop.name.removeDiacritics().contains(query, ignoreCase = true)
                        val matchesLine = stop.lines.any { it.removeDiacritics().equals(query, ignoreCase = true) }

                        if (selectedFilter == "Stops") {
                            matchesName || matchesLine
                        } else {
                            matchesName || matchesLine || routeStops.contains(stop)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stops Map") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Favorites.route) }) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Go to Favorites"
                        )
                    }
                    IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile"
                        )
                    }
//                    IconButton(
//                        onClick = {
//                            coroutineScope.launch {
//                                userManager.setLoggedIn(false)
//                                Firebase.auth.signOut()
//                                navController.navigate(Screen.Guest.route) {
//                                    popUpTo(Screen.Main.route) { inclusive = true }
//                                }
//                            }
//                        }
//                    ) {
//                        Icon(
//                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
//                            contentDescription = "Logout"
//                        )
//                    }
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
            var clusterManager by remember { mutableStateOf<ClusterManager<BusStop>?>(null) }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                contentPadding = PaddingValues(bottom = if (selectedStop != null) 140.dp else 16.dp),
                properties = mapProperties,
                uiSettings = MapUiSettings(myLocationButtonEnabled = true)
            ) {
                MapEffect(Unit) { map ->
                    if (clusterManager == null) {
                        val manager = ClusterManager<BusStop>(context, map)
                        val algorithm = NonHierarchicalDistanceBasedAlgorithm<BusStop>()
                        manager.algorithm = PreCachingAlgorithmDecorator(algorithm)
                        val renderer = DefaultClusterRenderer<BusStop>(context, map, manager)
                        renderer.minClusterSize = 4
                        renderer.setAnimation(false)
                        manager.renderer = renderer
                        manager.setOnClusterItemClickListener { stop ->
                            selectedStop = stop
                            false
                        }
                        manager.setOnClusterClickListener {
                            false
                        }
                        map.setOnMapClickListener {
                            selectedStop = null
                        }
                        map.setOnCameraIdleListener(manager)
                        map.setOnMarkerClickListener(manager)
                        clusterManager = manager
                    }
                }

                LaunchedEffect(filteredStops, clusterManager) {
                    clusterManager?.let { manager ->
                        manager.clearItems()
                        manager.addItems(filteredStops)
                        manager.cluster()
                    }
                }

                activeRoutes.forEach { route ->
                    Polyline(
                        points = route.points,
                        color = route.color,
                        width = 10f,
                        geodesic = true
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(if (selectedFilter == "Lines") "Ex: 36, 4" else "Search...") },

                    leadingIcon = {
                        Box {
                            Row(
                                modifier = Modifier
                                    .clickable { expandedFilterMenu = true }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = selectedFilter, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }

                            DropdownMenu(
                                expanded = expandedFilterMenu,
                                onDismissRequest = { expandedFilterMenu = false }
                            ) {
                                filterOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            selectedFilter = option
                                            expandedFilterMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    },

                    trailingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search icon")
                    },

                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Go
                    )
                )
            }

            val fabBottomPadding by animateDpAsState(
                targetValue = if (selectedStop != null) 300.dp else 16.dp,
                label = "fabAnimation"
            )
            FloatingActionButton(
                onClick = {
                    navController.navigate(Screen.Alerts.route)
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = fabBottomPadding),
                containerColor = MaterialTheme.colorScheme.errorContainer
            ) {
                Icon(
                    imageVector = Icons.Default.BusAlert,
                    contentDescription = "Traffic Alerts"
                )
            }

            if (selectedStop != null) {
                val stop = selectedStop!!
                val isFavorite = favoriteStops.contains(stop.name)

                val currentStopInfo = remember(stop, busStops) {
                    busStops.find { it.id == stop.id } ?: stop
                }

                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = currentStopInfo.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Lines: ${currentStopInfo.lines.joinToString(", ")}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val extraInfo = mutableListOf<String>()
                            if (currentStopInfo.hasBench) extraInfo.add("Bench")
                            if (currentStopInfo.hasShelter) extraInfo.add("Shelter")
                            if (extraInfo.isNotEmpty()) {
                                Text(
                                    text = extraInfo.joinToString(" - "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Text(
                                text = scheduleResponse,
                                style = if (isUrgent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                                color = if (isUrgent) Color.Red else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = {
                                if (currentUser != null) {
                                    val userRef = db.collection("users").document(currentUser.uid)
                                    coroutineScope.launch(Dispatchers.IO) {
                                        if (isFavorite) {
                                            favoriteDao.deleteFavorite(currentUser.uid, stop.name)
                                            userRef.update(
                                                "favorites",
                                                FieldValue.arrayRemove(stop.name)
                                            )
                                        } else {
                                            favoriteDao.insertFavorite(
                                                ro.unitbv.wheresmybus.data.FavoriteEntity(
                                                    currentUser.uid,
                                                    stop.name
                                                )
                                            )
                                            userRef.update(
                                                "favorites",
                                                FieldValue.arrayUnion(stop.name)
                                            )
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Toggle Favorites",
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

suspend fun seedDatabaseFromJson(context: Context, filename: String): List<BusStop> =
    withContext(Dispatchers.IO) {
        val db = FirebaseFirestore.getInstance()
        val seededStops = mutableListOf<BusStop>()

        try {
            val jsonString = context.assets.open(filename).bufferedReader().use { it.readText() }
            val rootObject = JSONObject(jsonString)

            val busStopsArray =
                rootObject.optJSONArray("elements") ?: rootObject.optJSONArray("bus_stops")

            if (busStopsArray == null) {
                Log.e("Seeding", "Didn't find any valid JSON list!")
                return@withContext emptyList()
            }

            var stopsAdded = 0
            for (i in 0 until busStopsArray.length()) {
                val busStop = busStopsArray.getJSONObject(i)

                if (busStop.optString("type") == "node") {
                    val tags = busStop.optJSONObject("tags")

                    if (tags != null && (tags.optString("highway") == "bus_stop" || tags.optString("public_transport") == "platform")) {
                        val stopId = busStop.getLong("id")
                        val name = tags.optString("name", "Unknown stop")
                        val lat = busStop.getDouble("lat")
                        val lng = busStop.optDouble("lon", busStop.optDouble("lng", 0.0))

                        val linesString = tags.optString("lines", "")
                        val linesList = if (linesString.isNotBlank()) {
                            linesString.split(",").map { it.trim() }
                        } else {
                            emptyList()
                        }

                        val hasBench = tags.optString("bench") == "yes"
                        val hasShelter = tags.optString("shelter") == "yes"
                        val hasRealTimeBoard = tags.optString("departures_board") == "realtime"
                        val wheelchairAccess = tags.optString("wheelchair", "Unknown")
                        val operator = tags.optString("operator", "RAT Brasov")

                        val stopData = hashMapOf(
                            "id" to stopId,
                            "name" to name,
                            "lat" to lat,
                            "lng" to lng,
                            "lines" to linesList,
                            "hasBench" to hasBench,
                            "hasShelter" to hasShelter,
                            "hasRealTimeBoard" to hasRealTimeBoard,
                            "wheelchairAccess" to wheelchairAccess,
                            "operator" to operator
                        )

                        db.collection("bus_stops").document(stopId.toString()).set(stopData)

                        seededStops.add(
                            BusStop(
                                id = stopId,
                                name = name,
                                location = LatLng(lat, lng),
                                lines = linesList,
                                hasBench = hasBench,
                                hasShelter = hasShelter,
                                hasRealTimeBoard = hasRealTimeBoard,
                                wheelchairAccess = wheelchairAccess,
                                operator = operator
                            )
                        )
                        stopsAdded++
                    }
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Database seeded with $stopsAdded stops!",
                    Toast.LENGTH_LONG
                ).show()
            }
            Log.d("Seeding", "Saved $stopsAdded stops.")

        } catch (exception: Exception) {
            Log.e("Seeding error", "Error at processing JSON: ${exception.message}", exception)
        }

        return@withContext seededStops
    }

suspend fun loadRoutesFromJson(
    context: Context,
    filename: String,
    availableStops: List<BusStop>
): List<BusRoute> = withContext(Dispatchers.IO) {
    val loadedRoutes = mutableListOf<BusRoute>()

    try {
        val jsonString = context.assets.open(filename).bufferedReader().use { it.readText() }
        val rootObject = JSONObject(jsonString)
        val elementsArray = rootObject.optJSONArray("elements") ?: return@withContext emptyList()

        val allWays = mutableMapOf<Long, List<LatLng>>()
        for (i in 0 until elementsArray.length()) {
            val elem = elementsArray.getJSONObject(i)
            if (elem.optString("type") == "way") {
                val wayId = elem.optLong("id")
                val wayPoints = mutableListOf<LatLng>()
                val geometry = elem.optJSONArray("geometry")
                if (geometry != null) {
                    for (k in 0 until geometry.length()) {
                        val geoPoint = geometry.getJSONObject(k)
                        val lat = geoPoint.optDouble("lat", Double.NaN)
                        val lon = geoPoint.optDouble("lon", Double.NaN)
                        if (!lat.isNaN() && !lon.isNaN()) wayPoints.add(LatLng(lat, lon))
                    }
                }
                allWays[wayId] = wayPoints
            }
        }

        for (i in 0 until elementsArray.length()) {
            val relation = elementsArray.getJSONObject(i)

            if (relation.optString("type") == "relation") {
                val tags = relation.optJSONObject("tags")
                if (tags != null && tags.optString("type") == "route" &&
                    (tags.optString("route") == "bus" || tags.optString("route") == "trolleybus")) {

                    val lineName = tags.optString("ref", "Unknown")
                    val hexColor = tags.optString("colour", "#9C27B0")
                    val routeColor = try {
                        Color(parseColor(if (!hexColor.startsWith("#")) "#$hexColor" else hexColor))
                    } catch (e: Exception) { Color(0xFF9C27B0) }

                    val membersArray = relation.optJSONArray("members")
                    val routePoints = mutableListOf<LatLng>()
                    val routeStops = mutableSetOf<BusStop>()

                    if (membersArray != null) {
                        for (j in 0 until membersArray.length()) {
                            val member = membersArray.getJSONObject(j)
                            val type = member.optString("type")
                            val refId = member.optLong("ref", -1L)

                            if (type == "way") {
                                val wayPoints = mutableListOf<LatLng>()
                                allWays[refId]?.let { wayPoints.addAll(it) }

                                val geometry = member.optJSONArray("geometry")
                                if (geometry != null) {
                                    for (k in 0 until geometry.length()) {
                                        val geoPoint = geometry.getJSONObject(k)
                                        val lat = geoPoint.optDouble("lat", Double.NaN)
                                        val lon = geoPoint.optDouble("lon", Double.NaN)
                                        if (!lat.isNaN() && !lon.isNaN()) wayPoints.add(LatLng(lat, lon))
                                    }
                                }

                                if (wayPoints.isNotEmpty()) {
                                    if (routePoints.isEmpty()) {
                                        routePoints.addAll(wayPoints)
                                    } else {
                                        val lastDrawnPoint = routePoints.last()
                                        val firstWayPoint = wayPoints.first()
                                        val lastWayPoint = wayPoints.last()

                                        val dLatFirst = lastDrawnPoint.latitude - firstWayPoint.latitude
                                        val dLonFirst = lastDrawnPoint.longitude - firstWayPoint.longitude
                                        val distToFirst = (dLatFirst * dLatFirst) + (dLonFirst * dLonFirst)

                                        val dLatLast = lastDrawnPoint.latitude - lastWayPoint.latitude
                                        val dLonLast = lastDrawnPoint.longitude - lastWayPoint.longitude
                                        val distToLast = (dLatLast * dLatLast) + (dLonLast * dLonLast)

                                        if (distToLast < distToFirst) {
                                            routePoints.addAll(wayPoints.reversed())
                                        } else {
                                            routePoints.addAll(wayPoints)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (routePoints.isNotEmpty()) {
                        val stopNodeRefs = mutableSetOf<Long>()
                        val stopNodeCoords = mutableListOf<Pair<Double, Double>>()

                        if(membersArray != null) {
                            for(j in 0 until membersArray.length()){
                                val member = membersArray.getJSONObject(j)
                                if(member.optString("type") == "node") {
                                    val role = member.optString("role")
                                    if(role == "stop" || role == "platform" || role == "stop_entry_only" || role == "stop_exit_only") {
                                        stopNodeRefs.add(member.optLong("ref", -1L))
                                        val lat = member.optDouble("lat", Double.NaN)
                                        val lon = member.optDouble("lon", Double.NaN)
                                        if(!lat.isNaN() && !lon.isNaN()) {
                                            stopNodeCoords.add(Pair(lat, lon))
                                        }
                                    }
                                }
                            }
                        }
                        for (stop in availableStops) {
                            if(stop.id in stopNodeRefs) {
                                routeStops.add(stop)
                            } else {
                                for ((nodeLat, nodeLon) in stopNodeCoords) {
                                    val dLat = Math.abs(nodeLat - stop.location.latitude)
                                    val dLon = Math.abs(nodeLon - stop.location.longitude)
                                    if(dLat < 0.0003 && dLon < 0.0003){
                                        routeStops.add(stop)
                                        break
                                    }
                                }
                            }
                        }
                        loadedRoutes.add(BusRoute(lineName, routeColor, routePoints, routeStops.toList()))
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("Routes", "Error parsing routes: ${e.message}")
    }

    return@withContext loadedRoutes
}

fun String.removeDiacritics(): String{
    val normalizedString = Normalizer.normalize(this, Normalizer.Form.NFD)
    val regex = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    return regex.replace(normalizedString, "")
}