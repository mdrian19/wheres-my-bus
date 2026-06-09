package ro.unitbv.wheresmybus.screens

import android.graphics.Camera
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import ro.unitbv.wheresmybus.models.Screen

@Composable
fun GuestScreen(navController: NavController) {
    val brasov = LatLng(45.657974, 25.601198)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(brasov, 13f)
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            contentPadding = PaddingValues(bottom = navBarPadding + 80.dp)
        ) {
            Marker(
                state = MarkerState(position = LatLng(45.650000, 25.600000)),
                title = "Linia 4",
                snippet = "Apasa pentru detalii",
                onClick = {
                    navController.navigate(Screen.Login.route)
                    true
                }
            )
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