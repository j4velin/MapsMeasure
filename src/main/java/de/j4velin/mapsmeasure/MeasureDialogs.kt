/*
 * Copyright 2026 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.j4velin.mapsmeasure

import android.text.Spanned
import android.text.style.URLSpan
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

/**
 * The current distance and area in all supported units, and the metric/imperial switch
 */
@Composable
fun UnitsDialog(
    metric: Boolean,
    distance: Double,
    area: Double,
    onMetricChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val format = remember { Units.twoDecimals() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = metric, onValueChange = onMetricChange, role = Role.Checkbox),
                ) {
                    Checkbox(checked = metric, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.metric), style = MaterialTheme.typography.titleMedium)
                }
                UnitsSection(
                    R.drawable.ic_distance, R.string.measure_distance,
                    stringResource(
                        R.string.units_distance,
                        format.format(max(0.0, distance)),
                        format.format(distance / 1000),
                        format.format(max(0.0, distance / 0.3048)),
                        format.format(max(0.0, distance / 0.9144)),
                        format.format(distance / 1609.344),
                        format.format(distance / 1852),
                    )
                )
                UnitsSection(
                    R.drawable.ic_area, R.string.measure_area,
                    stringResource(
                        R.string.units_area,
                        format.format(max(0.0, area)),
                        format.format(area / 10000),
                        format.format(area / 1000000),
                        format.format(max(0.0, area / 0.09290304)),
                        format.format(area / 4046.8726099),
                        format.format(area / 2589988.110336),
                    )
                )
            }
        },
    )
}

@Composable
private fun UnitsSection(@DrawableRes icon: Int, @StringRes title: Int, values: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
        Icon(painterResource(icon), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
    }
    Text(values, modifier = Modifier.padding(start = 40.dp, top = 4.dp))
}

/**
 * Save, load or share the trace
 *
 * @param hasOldFiles true to offer the traces older app versions saved in the app's folder
 */
@Composable
fun SaveDialog(
    hasOldFiles: Boolean,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onShare: () -> Unit,
    onOldFiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
        text = {
            Column {
                DialogAction(R.drawable.ic_action_save, R.string.save, onSave)
                DialogAction(R.drawable.ic_action_load, R.string.load, onLoad)
                if (hasOldFiles) DialogAction(R.drawable.ic_action_load, R.string.old_files, onOldFiles)
                DialogAction(R.drawable.ic_action_share, R.string.share, onShare)
            }
        },
    )
}

@Composable
private fun DialogAction(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The traces older app versions saved in the app's folder: tap one to load it, or delete it
 */
@Composable
fun OldTracesDialog(
    files: List<File>,
    onLoad: (File) -> Unit,
    onDelete: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_file)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                files.forEach { file ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName(file),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onLoad(file) }
                                .padding(vertical = 12.dp),
                        )
                        IconButton(onClick = { onDelete(file) }) {
                            Icon(
                                painterResource(R.drawable.ic_action_delete),
                                contentDescription = stringResource(R.string.delete),
                            )
                        }
                    }
                }
            }
        },
    )
}

/**
 * @return the file name without extension, or the date for the "MapsMeasure_<time>" default names
 */
private fun displayName(file: File): String {
    val name = file.nameWithoutExtension
    val time = name.removePrefix("MapsMeasure_").takeIf { it != name }?.toLongOrNull()
    return if (time != null) DateFormat.getDateTimeInstance().format(Date(time)) else name
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(linkColor) {
        val about = context.resources.getText(R.string.about_text)
        buildAnnotatedString {
            append(about.toString())
            if (about is Spanned) {
                // keep the links of the string resource
                about.getSpans(0, about.length, URLSpan::class.java).forEach { span ->
                    addLink(
                        LinkAnnotation.Url(
                            span.url,
                            TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                        ),
                        about.getSpanStart(span),
                        about.getSpanEnd(span),
                    )
                }
            }
            append(context.getString(R.string.app_version, BuildConfig.VERSION_NAME))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about)) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
    )
}

@Composable
fun DeleteAllDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(pluralStringResource(R.plurals.delete_all, count, count)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(android.R.string.yes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.no)) }
        },
    )
}
