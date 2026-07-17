package com.clinic.neochild.features.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.R
import com.clinic.neochild.core.ui.AppBackground
import com.clinic.neochild.core.ui.StandardButton
import com.clinic.neochild.core.ui.StandardTextField
import com.clinic.neochild.core.designsystem.NeoChildTheme

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    
    val isLoading by viewModel.isLoading
    val error by viewModel.error

    LoginContent(
        email = email,
        onEmailChange = { email = it },
        password = password,
        onPasswordChange = { password = it },
        isLoading = isLoading,
        error = error,
        onLoginClick = { viewModel.login(email, password, onLoginSuccess) }
    )
}

@Composable
private fun LoginContent(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    error: String?,
    onLoginClick: () -> Unit
) {
    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(120.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Sign in to continue",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            StandardTextField(
                value = email,
                onValueChange = onEmailChange,
                label = "Email",
                placeholder = "Enter your email"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            StandardTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = "Password",
                placeholder = "Enter your password",
                visualTransformation = PasswordVisualTransformation()
            )
            
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            StandardButton(
                onClick = onLoginClick,
                isLoading = isLoading
            ) {
                Text("Login", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    NeoChildTheme {
        LoginContent(
            email = "admin@example.com",
            onEmailChange = {},
            password = "password",
            onPasswordChange = {},
            isLoading = false,
            error = null,
            onLoginClick = {}
        )
    }
}
