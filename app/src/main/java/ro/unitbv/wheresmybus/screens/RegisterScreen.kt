package ro.unitbv.wheresmybus.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import ro.unitbv.wheresmybus.models.Screen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import ro.unitbv.wheresmybus.data.UserManager
import ro.unitbv.wheresmybus.R

@Composable
fun RegisterScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    onRegisterClick: () -> Unit = {},
    onLoginClick: (email: String, pasword: String) -> Unit = { _, _ -> },
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var email by remember { mutableStateOf("") }
    var emailErr by remember { mutableStateOf<String?>(null) }
    var passwordErr by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var passwordVisibility by remember { mutableStateOf(false) }
    var passwordConf by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userManager = remember { UserManager(context) }

    val token = stringResource(R.string.default_web_client_id)
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(token)
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    val launcher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    val idToken = account.idToken
                    if (idToken != null) {
                        val credential = GoogleAuthProvider.getCredential(idToken, null)
                        Firebase.auth.signInWithCredential(credential)
                            .addOnCompleteListener { authTask ->
                                if (authTask.isSuccessful) {
                                    val user = Firebase.auth.currentUser
                                    val isNewUser =
                                        authTask.result?.additionalUserInfo?.isNewUser == true

                                    if (isNewUser && user != null) {
                                        val userData = hashMapOf(
                                            "name" to (user.displayName ?: "Google user"),
                                            "email" to (user.email ?: ""),
                                            "city" to "Unknown"
                                        )
                                        Firebase.firestore.collection("users").document(user.uid).set(userData)
                                    }

                                    coroutineScope.launch{
                                        userManager.setLoggedIn(true)
                                        navController.navigate(Screen.Main.route){
                                            popUpTo(Screen.Guest.route) { inclusive = true }
                                        }
                                    }
                                } else {
                                    emailErr = "Error on creating account with Google"
                                }
                            }
                    }
                } catch (e: ApiException) {
                    emailErr = "Log in with Google has been canceled"
                }
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Create a new account", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; emailErr = null },
                label = { Text("Name") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = surname,
                onValueChange = { surname = it; emailErr = null },
                label = { Text("Surname") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("City") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { newValue: String ->
                email = newValue
                emailErr = null
            },
            label = { Text("Email") },
            leadingIcon = {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null
                )
            },
            isError = emailErr?.let {
                true
            } ?: false,
            supportingText = emailErr?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { newValue: String ->
                password = newValue
                passwordErr = null
            },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisibility) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image =
                    if (passwordVisibility) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { passwordVisibility = !passwordVisibility }) {
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
            visualTransformation = if (passwordVisibility) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image =
                    if (passwordVisibility) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { passwordVisibility = !passwordVisibility }) {
                    Icon(imageVector = image, contentDescription = "Toggle password visibility")
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            )
        )

        if(emailErr != null){
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = emailErr!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (email.isNotBlank() && password.isNotBlank() && password == passwordConf && name.isNotBlank() && city.isNotBlank()) {
                    Firebase.auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener{ task ->
                            if(task.isSuccessful) {
                                val userId = Firebase.auth.currentUser?.uid ?: ""
                                val userData = hashMapOf(
                                    "name" to "$name $surname",
                                    "city" to city,
                                    "email" to email
                                )
                                Firebase.firestore.collection("users").document(userId).set(userData)
                                    .addOnSuccessListener{
                                        navController.popBackStack()
                                    }
                            } else {
                                emailErr = task.exception?.localizedMessage ?: "Error on creating account"
                            }
                        }
                } else {
                    emailErr = "Please check the field data and add the missing details!"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "OR")
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                val signInIntent = googleSignInClient.signInIntent
                launcher.launch(signInIntent)
            },
            modifier = Modifier.fillMaxWidth()
        ){
            Text("Register with Google")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.popBackStack() }) {
            Text("Already have an account? Back to Login")
        }
    }
}

@Composable
@Preview(showBackground = true, name = "Register Screen Preview")
fun RegisterScreenPreview() {
    val navController = rememberNavController()
    RegisterScreen(navController)
}