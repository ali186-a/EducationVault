package com.educationvault.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.educationvault.app.BuildConfig
import com.educationvault.app.R
import com.educationvault.app.docs.Formatting

// ---------- settings ----------

@Composable
internal fun SettingsScreen(authVm: AuthViewModel, vm: DocsViewModel) {
    DocsScaffold(R.string.settings_title, vm) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            LanguageSwitcher()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.settings_account), style = MaterialTheme.typography.titleMedium)
            Text(vm.me.email)
            Text(stringResource(R.string.upload_limit_note, Formatting.formatSize(vm.me.maxUploadBytes)))
            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.settings_password_hint), style = MaterialTheme.typography.bodySmall)
            Messages(vm.errorRes, vm.infoRes)
            OutlinedButton(
                onClick = { vm.sendPasswordResetToSelf() },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_password)) }
            OutlinedButton(onClick = { authVm.signOut() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.logout_button))
            }
        }
    }
}

// ---------- backup / storage information ----------

@Composable
private fun InfoSection(titleRes: Int, bodyRes: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(bodyRes))
    }
}

@Composable
internal fun BackupScreen(vm: DocsViewModel) {
    DocsScaffold(R.string.backup_title, vm) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.backup_count, vm.statsTotal?.toString() ?: "…"), style = MaterialTheme.typography.titleMedium)
            InfoSection(R.string.backup_where, R.string.backup_where_body)
            InfoSection(R.string.backup_trash, R.string.backup_trash_body)
            InfoSection(R.string.backup_copy, R.string.backup_copy_body)
            InfoSection(R.string.backup_space, R.string.backup_space_body)
            InfoSection(R.string.backup_privacy, R.string.backup_privacy_body)
        }
    }
}
