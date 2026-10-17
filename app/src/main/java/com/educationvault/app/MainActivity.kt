package com.educationvault.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.educationvault.app.ui.EducationVaultTheme
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Urdu (right-to-left) is the default interface language.
    override fun attachBaseContext(newBase: Context) {
        val locale = Locale("ur")
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EducationVaultTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    StatusScreen()
                }
            }
        }
    }
}

private sealed interface PingUi {
    data object Idle : PingUi
    data object Loading : PingUi
    data class Done(val messageRes: Int, val code: Int?, val good: Boolean) : PingUi
}

@Composable
private fun StatusScreen() {
    val scope = rememberCoroutineScope()
    var ping by remember { mutableStateOf<PingUi>(PingUi.Idle) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))

        if (!AppConfig.isConfigured) {
            // Never pretend login/upload works when configuration is missing.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.config_missing_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.config_missing_body),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    AppConfig.missingKeys.forEach {
                        Text(text = it, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        } else {
            Button(
                onClick = {
                    ping = PingUi.Loading
                    scope.launch {
                        val result = BackendPing.ping(AppConfig.appsScriptUrl)
                        ping = result.fold(
                            onSuccess = { outcome ->
                                when (outcome) {
                                    is PingOutcome.Reachable ->
                                        if (outcome.configured) PingUi.Done(R.string.ping_ok, null, true)
                                        else PingUi.Done(R.string.ping_not_configured, null, false)
                                    is PingOutcome.HttpError ->
                                        PingUi.Done(R.string.ping_http_error, outcome.code, false)
                                    PingOutcome.NotBackend ->
                                        PingUi.Done(R.string.ping_not_backend, null, false)
                                }
                            },
                            onFailure = { PingUi.Done(R.string.ping_network_error, null, false) }
                        )
                    }
                },
                enabled = ping !is PingUi.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.ping_button))
            }
            Spacer(Modifier.height(24.dp))
            when (val state = ping) {
                PingUi.Idle -> Unit
                PingUi.Loading -> CircularProgressIndicator()
                is PingUi.Done -> {
                    val text = if (state.code != null) stringResource(state.messageRes, state.code)
                    else stringResource(state.messageRes)
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.good) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
