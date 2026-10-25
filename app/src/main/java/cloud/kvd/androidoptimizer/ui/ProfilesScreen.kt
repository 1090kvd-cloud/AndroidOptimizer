package cloud.kvd.androidoptimizer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cloud.kvd.androidoptimizer.R
import cloud.kvd.androidoptimizer.domain.JournalEntry
import cloud.kvd.androidoptimizer.domain.Level
import cloud.kvd.androidoptimizer.domain.ProfileId
import cloud.kvd.androidoptimizer.domain.SettingField
import cloud.kvd.androidoptimizer.domain.SettingsValues
import cloud.kvd.androidoptimizer.domain.diff
import cloud.kvd.androidoptimizer.domain.isApplied
import cloud.kvd.androidoptimizer.domain.latestRevertable
import cloud.kvd.androidoptimizer.domain.profileValues
import java.text.DateFormat
import java.util.Date

@Composable
fun ProfilesScreen(
    state: UiState,
    onApply: (ProfileId) -> Unit,
    onRevert: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<ProfileId?>(null) }
    val revertableId = state.journal.latestRevertable()?.id

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!state.canWriteSettings) {
            item(key = "permission") {
                RecommendationCard(
                    level = Level.INFO,
                    title = stringResource(R.string.profiles_permission_title),
                    body = stringResource(R.string.profiles_permission_body),
                    actionLabel = stringResource(R.string.action_grant),
                    onAction = { context.openFirstAvailable(*writeSettingsIntents(context.packageName)) }
                )
            }
        }

        items(ProfileId.entries, key = { it.name }) { id ->
            ProfileCard(
                id = id,
                current = state.currentSettings,
                canApply = state.canWriteSettings,
                onRequestApply = { pending = id }
            )
        }

        item(key = "journal_title") { SectionTitle(stringResource(R.string.section_journal)) }
        if (state.journal.isEmpty()) {
            item(key = "journal_empty") {
                Text(
                    stringResource(R.string.journal_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.journal, key = { it.id }) { entry ->
                JournalRow(entry = entry, canRevert = entry.id == revertableId, onRevert = onRevert)
            }
        }
    }

    pending?.let { id ->
        ConfirmDialog(
            id = id,
            current = state.currentSettings,
            onConfirm = {
                onApply(id)
                pending = null
            },
            onDismiss = { pending = null }
        )
    }
}

@Composable
private fun ProfileCard(
    id: ProfileId,
    current: SettingsValues,
    canApply: Boolean,
    onRequestApply: () -> Unit
) {
    val context = LocalContext.current
    val target = profileValues(id)
    val active = isApplied(current, target)
    val lines = SettingField.entries.mapNotNull { field ->
        target.valueOf(field)?.let { value ->
            context.getString(field.labelRes()) + ": " + formatSettingValue(context, field, value)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(id.titleRes()), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(id.descRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (active) {
                    Text(
                        stringResource(R.string.profile_active),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Button(onClick = onRequestApply, enabled = canApply) { Text(stringResource(R.string.action_apply)) }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    id: ProfileId,
    current: SettingsValues,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val changes = diff(current, profileValues(id)).map { formatChange(context, it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id.titleRes())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                changes.forEach { Text(it) }
                Text(
                    stringResource(R.string.profile_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun JournalRow(entry: JournalEntry, canRevert: Boolean, onRevert: (Long) -> Unit) {
    val context = LocalContext.current
    val date = remember(entry.id) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.id))
    }
    val changes = diff(entry.before, entry.after).joinToString("\n") { formatChange(context, it) }

    ListItem(
        headlineContent = { Text(stringResource(entry.profile.titleRes())) },
        supportingContent = { Text(if (changes.isEmpty()) date else "$date\n$changes") },
        trailingContent = {
            when {
                entry.reverted -> Text(
                    stringResource(R.string.journal_reverted),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                canRevert -> TextButton(onClick = { onRevert(entry.id) }) { Text(stringResource(R.string.action_revert)) }
            }
        }
    )
}
