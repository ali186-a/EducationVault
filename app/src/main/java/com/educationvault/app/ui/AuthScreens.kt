package com.educationvault.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.educationvault.app.AppConfig
import com.educationvault.app.R
import com.educationvault.app.auth.GoogleSignInHelper

// ---------- building blocks ----------

@Composable
internal fun ScreenFrame(content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
        LanguageSwitcher(Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

@Composable
internal fun Title(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
internal fun Messages(errorRes: Int?, infoRes: Int?) {
    if (errorRes != null) {
        Text(
            text = stringResource(errorRes),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
    }
    if (infoRes != null) {
        Text(
            text = stringResource(infoRes),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.email_label)) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
    )
}

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    labelRes: Int,
    enabled: Boolean,
    imeAction: ImeAction
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(stringResource(if (visible) R.string.hide_password else R.string.show_password))
            }
        }
    )
}

@Composable
internal fun BusyButton(textRes: Int, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        else Text(stringResource(textRes))
    }
}

// ---------- root ----------

@Composable
fun AppRoot(vm: AuthViewModel = viewModel()) {
    if (!AppConfig.isConfigured) {
        ConfigErrorScreen()
        return
    }
    when (val s = vm.state) {
        AuthUiState.Restoring -> LoadingScreen()
        is AuthUiState.RestoreFailed -> RetryScreen(s.messageRes, onRetry = vm::retryRestore)
        AuthUiState.SignedOut -> SignedOutFlow(vm)
        is AuthUiState.NeedsVerification -> VerifyScreen(vm, s.email)
        is AuthUiState.NotInvited -> NotInvitedScreen(vm, s.email)
        is AuthUiState.Ready -> DocsRoot(vm, s.me, s.session, s.profile)
    }
}

@Composable
private fun SignedOutFlow(vm: AuthViewModel) {
    var screen by rememberSaveable { mutableStateOf("login") }
    var email by rememberSaveable { mutableStateOf("") }
    val go = { target: String -> vm.clearMessages(); screen = target }
    when (screen) {
        "register" -> RegisterScreen(vm, email, { email = it }, onBack = { go("login") })
        "reset" -> ResetScreen(vm, email, { email = it }, onBack = { go("login") })
        else -> LoginScreen(vm, email, { email = it }, onRegister = { go("register") }, onReset = { go("reset") })
    }
}

// ---------- screens ----------

@Composable
private fun LoadingScreen() = ScreenFrame {
    CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.restoring_session))
}

@Composable
private fun RetryScreen(messageRes: Int, onRetry: () -> Unit) = ScreenFrame {
    Text(stringResource(messageRes), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onRetry) { Text(stringResource(R.string.retry_button)) }
}

@Composable
fun ConfigErrorScreen() = ScreenFrame {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.config_missing_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.config_missing_body), color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.height(8.dp))
            AppConfig.missingKeys.forEach { Text(it, color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

@Composable
private fun LoginScreen(
    vm: AuthViewModel,
    email: String,
    onEmail: (String) -> Unit,
    onRegister: () -> Unit,
    onReset: () -> Unit
) = ScreenFrame {
    var password by remember { mutableStateOf("") }
    Title(R.string.login_title)
    EmailField(email, onEmail, !vm.busy)
    Spacer(Modifier.height(8.dp))
    PasswordField(password, { password = it }, R.string.password_label, !vm.busy, ImeAction.Done)
    Messages(vm.errorRes, vm.infoRes)
    Spacer(Modifier.height(8.dp))
    BusyButton(R.string.login_button, vm.busy) { vm.signIn(email, password) }
    GoogleButton(vm)
    TextButton(onClick = onReset, enabled = !vm.busy) { Text(stringResource(R.string.reset_link)) }
    TextButton(onClick = onRegister, enabled = !vm.busy) { Text(stringResource(R.string.register_link)) }
}

@Composable
private fun GoogleButton(vm: AuthViewModel) {
    if (!AppConfig.isGoogleConfigured) return
    val context = LocalContext.current
    val ink = Color(0xFF1F1F1F)
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.or_divider),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    // Light "Sign in with Google" button: white, thin grey border, four-colour G
    Surface(
        onClick = {
            vm.signInWithGoogle { GoogleSignInHelper.fetchIdToken(context, AppConfig.googleWebClientId) }
        },
        enabled = !vm.busy,
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        contentColor = ink,
        border = BorderStroke(1.dp, Color(0xFF747775)),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(painter = painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.google_button), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ink)
        }
    }
    vm.errorDetail?.let {
        // selectable: long-press to copy the technical details
        SelectionContainer {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun RegisterScreen(
    vm: AuthViewModel,
    email: String,
    onEmail: (String) -> Unit,
    onBack: () -> Unit
) = ScreenFrame {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Title(R.string.register_title)
    Text(stringResource(R.string.register_note), style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(12.dp))
    EmailField(email, onEmail, !vm.busy)
    Spacer(Modifier.height(8.dp))
    PasswordField(password, { password = it }, R.string.password_label, !vm.busy, ImeAction.Next)
    Spacer(Modifier.height(8.dp))
    PasswordField(confirm, { confirm = it }, R.string.confirm_password_label, !vm.busy, ImeAction.Done)
    Messages(vm.errorRes, vm.infoRes)
    Spacer(Modifier.height(8.dp))
    BusyButton(R.string.register_button, vm.busy) { vm.register(email, password, confirm) }
    GoogleButton(vm)
    TextButton(onClick = onBack, enabled = !vm.busy) { Text(stringResource(R.string.back_to_login)) }
}

@Composable
private fun ResetScreen(
    vm: AuthViewModel,
    email: String,
    onEmail: (String) -> Unit,
    onBack: () -> Unit
) = ScreenFrame {
    Title(R.string.reset_title)
    EmailField(email, onEmail, !vm.busy)
    Messages(vm.errorRes, vm.infoRes)
    Spacer(Modifier.height(8.dp))
    BusyButton(R.string.reset_button, vm.busy) { vm.resetPassword(email) }
    TextButton(onClick = onBack, enabled = !vm.busy) { Text(stringResource(R.string.back_to_login)) }
}

@Composable
private fun VerifyScreen(vm: AuthViewModel, email: String) = ScreenFrame {
    Title(R.string.verify_title)
    Text(stringResource(R.string.verify_body, email), textAlign = TextAlign.Center)
    Messages(vm.errorRes, vm.infoRes)
    Spacer(Modifier.height(12.dp))
    BusyButton(R.string.verify_check_button, vm.busy) { vm.recheckAccess() }
    OutlinedButton(onClick = vm::resendVerification, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.verify_resend_button))
    }
    TextButton(onClick = vm::signOut, enabled = !vm.busy) { Text(stringResource(R.string.logout_button)) }
}

@Composable
private fun NotInvitedScreen(vm: AuthViewModel, email: String) = ScreenFrame {
    Title(R.string.not_invited_title)
    Text(stringResource(R.string.not_invited_body, email), textAlign = TextAlign.Center)
    Messages(vm.errorRes, vm.infoRes)
    Spacer(Modifier.height(12.dp))
    BusyButton(R.string.retry_button, vm.busy) { vm.recheckInvitation() }
    TextButton(onClick = vm::signOut, enabled = !vm.busy) { Text(stringResource(R.string.logout_button)) }
}
