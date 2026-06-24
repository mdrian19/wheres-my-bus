package ro.unitbv.wheresmybus.screens

import android.os.UserManager
import android.widget.Switch
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import androidx.datastore.preferences.core.edit
import ro.unitbv.wheresmybus.data.settingsDataStore
import ro.unitbv.wheresmybus.data.DARK_MODE_KEY
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.ui.semantics.Role.Companion.Switch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ro.unitbv.wheresmybus.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController){
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = Firebase.auth
    val user = auth.currentUser
    val isDark by remember(context) {
        context.settingsDataStore.data.map { it[DARK_MODE_KEY] ?: false }
    }.collectAsState(initial = false)
    val userManager = remember { UserManager(context) }

    var newPassword by remember {mutableStateOf("")}
    var isLoading by remember { mutableStateOf(false)}
    var passwordVisibility by remember { mutableStateOf(false) }
    var passwordConfVisibility by remember { mutableStateOf(false) }
    var passwordConf by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(
                        onClick = {navController.popBackStack()}
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                "Email: ${user?.email ?: "N/A"}",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            OutlinedTextField(
                value = newPassword,
                onValueChange = {newPassword = it},
                label = {Text("New Password")},
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisibility) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image =
                        if (passwordVisibility) Icons.Default.Visibility else Icons.Default.VisibilityOff
                    IconButton(
                        onClick = { passwordVisibility = !passwordVisibility },
                        modifier = Modifier.focusProperties { canFocus = false }
                    ) {
                        Icon(imageVector = image, contentDescription = "Toggle password visibility")
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                )
            )
            OutlinedTextField(
                value = passwordConf,
                onValueChange = { passwordConf = it },
                label = { Text("Confirm Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordConfVisibility) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image =
                        if (passwordConfVisibility) Icons.Default.Visibility else Icons.Default.VisibilityOff
                    IconButton(
                        onClick = { passwordConfVisibility = !passwordConfVisibility },
                        modifier = Modifier.focusProperties{ canFocus = false }
                    ) {
                        Icon(imageVector = image, contentDescription = "Toggle password visibility")
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Button(
                onClick = {
                    if (newPassword.isBlank()){
                        Toast.makeText(
                            context,
                            "Please set a password",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }
                    if (newPassword.length < 6) {
                        Toast.makeText(
                            context,
                            "Password too short",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }
                    if (newPassword != passwordConf){
                        Toast.makeText(
                            context,
                            "New password and confirmed password not matching",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }

                    isLoading = true

                    if (user == null) {
                        isLoading = false
                        Toast.makeText(
                            context,
                            "User not authenticated",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }

                    user.updatePassword(newPassword)
                        .addOnCompleteListener { task ->
                            isLoading = false
                            if (task.isSuccessful) {
                                Toast.makeText(
                                    context,
                                    "Password updated!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val error = task.exception?.message
                                if (error?.contains("requires recent login", ignoreCase = true) == true){
                                    Toast.makeText(
                                        context,
                                        "Security: please logout and login again",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else{
                                    Toast.makeText(
                                        context,
                                        "Error: ${error}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text("Update Password")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Dark Mode", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isDark,
                    onCheckedChange = { checked ->
                        scope.launch {
                            context.settingsDataStore.edit { it[DARK_MODE_KEY] = checked }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = {
                    scope.launch{
                        auth.signOut()
                        userManager.setLoggedIn(loggedIn = false)
                        navController.navigate("guest_screen") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Logout")
            }
        }
    }
}