package ro.unitbv.wheresmybus.screens

import android.content.Context
import android.graphics.Color.parseColor
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
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
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import org.json.JSONObject
import ro.unitbv.wheresmybus.data.UserManager
import ro.unitbv.wheresmybus.models.Screen
import ro.unitbv.wheresmybus.data.DatabaseProvider
import androidx.compose.runtime.Immutable
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm
import com.google.maps.android.clustering.algo.PreCachingAlgorithmDecorator
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.Polyline
import androidx.compose.runtime.collectAsState
import ro.unitbv.wheresmybus.network.RetrofitClient
import ro.unitbv.wheresmybus.network.UserData

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
                        scheduleResponse = "No available buses."
                    }
                } catch (e: Exception) {
                    scheduleResponse = "Schedule unavailable."
                    isUrgent = false
                }
                kotlinx.coroutines.delay(5000)
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
                    val finalStops = if (result.isEmpty) {
                        Log.d("FirebaseInit", "Database is empty. Starting seeding...")
                        seedDatabaseFromJson(context, "stops.json")
                    } else {
                        val fetchedStops = mutableListOf<BusStop>()
                        for (document in result) {
                            val id = document.getLong("id") ?: 0L
                            val name = document.getString("name") ?: "Unknown"
                            val lat = document.getDouble("lat") ?: 0.0
                            val lng = document.getDouble("lng") ?: 0.0
                            val lines = document.get("lines") as? List<String> ?: emptyList()
                            val hasBench = document.getBoolean("hasBench") ?: false
                            val hasShelter = document.getBoolean("hasShelter") ?: false
                            val hasRealTimeBoard = document.getBoolean("hasRealTimeBoard") ?: false
                            val wheelchairAccess =
                                document.getString("wheelchairAccess") ?: "unknown"
                            val operator = document.getString("operator") ?: "RAT Brașov"
                            fetchedStops.add(
                                BusStop(
                                    id = id,
                                    name = name,
                                    location = LatLng(lat, lng),
                                    lines = lines,
                                    hasBench = hasBench,
                                    hasShelter = hasShelter,
                                    hasRealTimeBoard = hasRealTimeBoard,
                                    wheelchairAccess = wheelchairAccess,
                                    operator = operator
                                )
                            )
                        }
                        fetchedStops
                    }

                    withContext(Dispatchers.Main) {
                        busStops = finalStops.toList()
                    }

                    val loadedRoutes = loadRoutesFromJson(context, "lines.json", finalStops)

                    withContext(Dispatchers.Main) {
                        busRoutes = loadedRoutes.toList()

                        busStops = finalStops.map { stop ->
                            val linesPassingHere = loadedRoutes
                                .filter { route -> route.stops.any { it.id == stop.id } }
                                .map { it.lineName }

                            val allLinesForStop =
                                (stop.lines + linesPassingHere).distinct().sorted()
                            stop.copy(lines = allLinesForStop)
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
            val query = searchQuery.trim()
            if (query.isBlank()) {
                emptyList()
            } else {
                busRoutes.filter { it.lineName.equals(searchQuery, ignoreCase = true) }
            }
        }
    }

    val filteredStops by remember {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                busStops
            } else {
                val routeStops = activeRoutes.flatMap { it.stops }.toSet()
                busStops.filter { stop ->
                    stop.name.contains(
                        searchQuery,
                        ignoreCase = true
                    ) || stop.lines.any {
                        it.equals(
                            searchQuery,
                            ignoreCase = true
                        )
                    } || routeStops.contains(stop)
                }
            }
        }
    }

    val onClusterItemClick: (BusStop) -> Boolean = remember {
        { stop ->
            selectedStop = stop
            false
        }
    }

    val onClusterClick: (Cluster<BusStop>) -> Boolean = remember {
        { false }
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
            var clusterManager by remember { mutableStateOf<ClusterManager<BusStop>?>(null) }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                contentPadding = PaddingValues(bottom = if (selectedStop != null) 140.dp else 16.dp)
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search line(e.g. 36, 4)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
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

            if (selectedStop != null) {
                val stop = selectedStop!!
                val isFavorite = favoriteStops.contains(stop.name)

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
                            Text(text = stop.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Lines: ${stop.lines.joinToString(", ")}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val extraInfo = mutableListOf<String>()
                            if (stop.hasBench) extraInfo.add("Bench")
                            if (stop.hasShelter) extraInfo.add("Shelter")
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
        val stopsMap = availableStops.associateBy { it.id }

        val allNodes = mutableMapOf<Long, LatLng>()
        for (i in 0 until elementsArray.length()) {
            val elem = elementsArray.getJSONObject(i)
            if (elem.optString("type") == "node") {
                val lat = elem.optDouble("lat")
                val lon = elem.optDouble("lon")
                if (!lat.isNaN() && !lon.isNaN()) {
                    allNodes[elem.optLong("id")] = LatLng(lat, lon)
                }
            }
        }

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
                        val lat = geoPoint.optDouble("lat")
                        val lon = geoPoint.optDouble("lon")
                        if (!lat.isNaN() && !lon.isNaN())
                            wayPoints.add(LatLng(lat, lon))
                    }
                } else {
                    val nodes = elem.optJSONArray("nodes")
                    if (nodes != null) {
                        for (k in 0 until nodes.length()) {
                            val nodeId = nodes.optLong(k)
                            allNodes[nodeId]?.let { wayPoints.add(it) }
                        }
                    }
                }
                allWays[wayId] = wayPoints
            }
        }
        for (i in 0 until elementsArray.length()) {
            val relation = elementsArray.getJSONObject(i)

            if (relation.optString("type") == "relation") {
                val tags = relation.optJSONObject("tags")

                if (tags != null && tags.optString("type") == "route" && (tags.optString("route") == "bus" || tags.optString(
                        "route"
                    ) == "trolleybus")
                ) {
                    val lineName = tags.optString("ref", "Unknown")

                    val hexColor = tags.optString("colour", "#9C27B0")
                    val routeColor = try {
                        Color(parseColor(if (!hexColor.startsWith("#")) "#$hexColor" else hexColor))
                    } catch (e: Exception) {
                        Color(0xFF9C27B0)
                    }

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
                                        val lat = geoPoint.optDouble("lat")
                                        val lon = geoPoint.optDouble("lon")
                                        if (!lat.isNaN() && !lon.isNaN()) wayPoints.add(
                                            LatLng(
                                                lat,
                                                lon
                                            )
                                        )
                                    }
                                }

                                if (wayPoints.isNotEmpty()) {
                                    if (routePoints.isEmpty()) {
                                        routePoints.addAll(wayPoints)
                                    } else {
                                        val lastDrawnPoint = routePoints.last()
                                        val firstWayPoint = wayPoints.first()
                                        val lastWayPoint = wayPoints.last()

                                        val dLatFirst =
                                            lastDrawnPoint.latitude - firstWayPoint.latitude
                                        val dLonFirst =
                                            lastDrawnPoint.longitude - firstWayPoint.longitude
                                        val distToFirst =
                                            (dLatFirst * dLatFirst) + (dLonFirst * dLonFirst)

                                        val dLatLast =
                                            lastDrawnPoint.latitude - lastWayPoint.latitude
                                        val dLonLast =
                                            lastDrawnPoint.longitude - lastWayPoint.longitude
                                        val distToLast =
                                            (dLatLast * dLatLast) + (dLonLast * dLonLast)

                                        if (distToLast < distToFirst) {
                                            routePoints.addAll(wayPoints.reversed())
                                        } else {
                                            routePoints.addAll(wayPoints)
                                        }
                                    }
                                }
                            } else if (type == "node") {
                                if (refId != -1L) {
                                    val foundStop = stopsMap[refId]
                                    if (foundStop != null) {
                                        routeStops.add(foundStop)
                                    }
                                }
                            }
                        }
                    }

                    if (routePoints.size > 1) {
                        var minLat = 90.0
                        var maxLat = -90.0
                        var minLon = 180.0
                        var maxLon = -180.0
                        for (p in routePoints) {
                            if (p.latitude < minLat) minLat = p.latitude
                            if (p.latitude > maxLat) maxLat = p.latitude
                            if (p.longitude < minLon) minLon = p.longitude
                            if (p.longitude > maxLon) maxLon = p.longitude
                        }

                        for (stop in availableStops) {
                            val sLat = stop.location.latitude
                            val sLon = stop.location.longitude

                            if (sLat < minLat - 0.0005 || sLat > maxLat + 0.0005 ||
                                sLon < minLon - 0.0005 || sLon > maxLon + 0.0005
                            ) {
                                continue
                            }

                            for (pt in routePoints) {
                                val dLat =
                                    if (pt.latitude > sLat) pt.latitude - sLat else sLat - pt.latitude
                                val dLon =
                                    if (pt.longitude > sLon) pt.longitude - sLon else sLon - pt.longitude

                                if (dLat < 0.0003 && dLon < 0.0003) {
                                    routeStops.add(stop)
                                    break
                                }
                            }
                        }
                        loadedRoutes.add(
                            BusRoute(
                                lineName = lineName,
                                color = routeColor,
                                points = routePoints,
                                stops = routeStops.toList()
                            )
                        )
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("Routes", "Error parsing routes: ${e.message}")
    }

    return@withContext loadedRoutes
}