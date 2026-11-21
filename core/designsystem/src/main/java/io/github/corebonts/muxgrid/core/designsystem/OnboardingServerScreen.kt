package io.github.corebonts.muxgrid.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.corebonts.muxgrid.core.model.ConnectionTestResult
import io.github.corebonts.muxgrid.core.model.ServerProfile
import kotlinx.coroutines.launch

@Composable
fun OnboardingServerScreen(
    initialProfile: ServerProfile,
    isTv: Boolean,
    canCancel: Boolean,
    onTestConnection: suspend (ServerProfile) -> ConnectionTestResult,
    onSaveAndConnect: suspend (ServerProfile) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val hostFocusRequester = remember { FocusRequester() }
    var host by rememberSaveable(initialProfile.id, initialProfile.host) {
        mutableStateOf(initialProfile.host)
    }
    var htspPortText by rememberSaveable(initialProfile.id, initialProfile.htspPort) {
        mutableStateOf(initialProfile.htspPort.toString())
    }
    var httpPortText by rememberSaveable(initialProfile.id, initialProfile.httpPort) {
        mutableStateOf(initialProfile.httpPort.toString())
    }
    var username by rememberSaveable(initialProfile.id, initialProfile.username) {
        mutableStateOf(initialProfile.username)
    }
    var password by rememberSaveable(initialProfile.id, initialProfile.password) {
        mutableStateOf(initialProfile.password)
    }

    var testing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ConnectionTestResult?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (isTv) {
            runCatching { hostFocusRequester.requestFocus() }
        }
    }

    fun buildProfile(): ServerProfile? {
        val result = validateServerProfileInput(
            profileId = initialProfile.id,
            rawHost = host,
            rawHtspPort = htspPortText,
            rawHttpPort = httpPortText,
            rawUsername = username,
            password = password,
        )
        return when (result) {
            is ServerValidationResult.Valid -> {
                validationError = null
                result.profile
            }
            is ServerValidationResult.Invalid -> {
                validationError = result.message
                null
            }
        }
    }

    val doTest: () -> Unit = {
        if (!testing && !saving) {
            val profile = buildProfile()
            if (profile != null) {
                testing = true
                testResult = null
                scope.launch {
                    try {
                        testResult = onTestConnection(profile)
                    } finally {
                        testing = false
                    }
                }
            }
        }
    }
    val doSave: () -> Unit = {
        if (!saving && !testing) {
            val profile = buildProfile()
            if (profile != null) {
                saving = true
                scope.launch {
                    try {
                        onSaveAndConnect(profile)
                    } finally {
                        saving = false
                    }
                }
            }
        }
    }

    val canvas = if (isTv) MuxGridColors.CanvasTv else MuxGridColors.CanvasPhone
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MuxGridColors.Accent,
        unfocusedBorderColor = MuxGridColors.HairlineBorder,
        focusedTextColor = MuxGridColors.TextPrimary,
        unfocusedTextColor = MuxGridColors.TextPrimary,
        focusedLabelColor = MuxGridColors.AccentSoft,
        unfocusedLabelColor = MuxGridColors.TextSecondary,
        cursorColor = MuxGridColors.AccentSoft,
    )

    val dpadFieldModifier = Modifier.onPreviewKeyEvent { ev ->
        if (!isTv || ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (ev.key) {
            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
            else -> false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvas)
            .padding(horizontal = if (isTv) 48.dp else 20.dp, vertical = if (isTv) 16.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (isTv) 680.dp else 540.dp)
                .fillMaxWidth()
                .clip(MuxGridShapes.Dialog)
                .background(MuxGridColors.CardSurface)
                .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Dialog)
                .verticalScroll(rememberScrollState())
                .padding(if (isTv) 20.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = MuxGridIcons.Dns,
                        contentDescription = null,
                        tint = MuxGridColors.AccentSoft,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "Connect to Tvheadend",
                        style = MuxGridType.Title,
                        fontSize = if (isTv) 20.sp else 22.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!isTv && canCancel) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(MuxGridShapes.Sm)
                            .clickable(enabled = !saving, onClick = onCancel),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MuxGridIcons.Close,
                            contentDescription = "Cancel",
                            tint = MuxGridColors.TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Text(
                text = "Uses HTSP (control, EPG, auth) and HTTP pass-through for instant broadcast playback.",
                style = MuxGridType.CardSubtitle,
            )

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Server Host / IP") },
                placeholder = { Text("tvheadend.local or 192.168.1.10") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(hostFocusRequester)
                    .then(dpadFieldModifier),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = htspPortText,
                    onValueChange = { htspPortText = it },
                    label = { Text("HTSP Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    colors = fieldColors,
                    modifier = Modifier.weight(1f).then(dpadFieldModifier),
                )
                OutlinedTextField(
                    value = httpPortText,
                    onValueChange = { httpPortText = it },
                    label = { Text("HTTP Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    colors = fieldColors,
                    modifier = Modifier.weight(1f).then(dpadFieldModifier),
                )
            }

            CredentialsFields(
                isTv = isTv,
                username = username,
                onUsernameChange = { username = it },
                password = password,
                onPasswordChange = { password = it },
                fieldColors = fieldColors,
                fieldModifier = dpadFieldModifier,
                onDone = doSave,
            )

            validationError?.let { err ->
                Text(
                    text = err,
                    color = MuxGridColors.CoralLive,
                    fontSize = 13.sp,
                )
            }

            testResult?.let { res ->
                val (bg, textColor, msg) = when (res) {
                    is ConnectionTestResult.Success -> Triple(
                        MuxGridColors.EmeraldOk.copy(alpha = MuxGridAlphas.SubtleFill),
                        MuxGridColors.EmeraldOk,
                        "Connected to ${res.serverName} ${res.serverVersion} (HTSP v${res.htspVersion}, DVR=${res.hasDvrAccess})",
                    )
                    is ConnectionTestResult.Failure -> Triple(
                        MuxGridColors.CoralLive.copy(alpha = MuxGridAlphas.SubtleFill),
                        MuxGridColors.CoralLive,
                        res.message,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MuxGridShapes.Badge)
                        .background(bg)
                        .padding(10.dp),
                ) {
                    Text(text = msg, color = textColor, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(2.dp))

            OnboardingActionButtons(
                isTv = isTv,
                canCancel = canCancel,
                testing = testing,
                saving = saving,
                onCancel = onCancel,
                onTest = doTest,
                onSave = doSave,
            )
        }
    }
}

@Composable
private fun CredentialsFields(
    isTv: Boolean,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    fieldColors: TextFieldColors,
    fieldModifier: Modifier,
    onDone: () -> Unit,
) {
    val renderUsername: @Composable (Modifier) -> Unit = { itemModifier ->
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Username") },
            placeholder = { Text("muxgrid") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors = fieldColors,
            modifier = itemModifier.then(fieldModifier),
        )
    }
    val renderPassword: @Composable (Modifier) -> Unit = { itemModifier ->
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            colors = fieldColors,
            modifier = itemModifier.then(fieldModifier),
        )
    }

    if (isTv) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            renderUsername(Modifier.weight(1f))
            renderPassword(Modifier.weight(1f))
        }
    } else {
        renderUsername(Modifier.fillMaxWidth())
        renderPassword(Modifier.fillMaxWidth())
    }
}

@Composable
private fun OnboardingActionButtons(
    isTv: Boolean,
    canCancel: Boolean,
    testing: Boolean,
    saving: Boolean,
    onCancel: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    val btnPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    var testFocused by remember { mutableStateOf(false) }
    var saveFocused by remember { mutableStateOf(false) }
    var cancelFocused by remember { mutableStateOf(false) }

    val renderSaveButton: @Composable (Modifier) -> Unit = { itemModifier ->
        Button(
            onClick = onSave,
            enabled = !saving && !testing,
            shape = MuxGridShapes.Sm,
            contentPadding = btnPadding,
            border = if (isTv && saveFocused) BorderStroke(MuxGridBorders.Focused, MuxGridColors.FocusBorder) else null,
            colors = ButtonDefaults.buttonColors(
                containerColor = MuxGridColors.Accent,
                contentColor = MuxGridColors.OnAccent,
            ),
            modifier = itemModifier.then(
                if (isTv) Modifier.onFocusChanged { saveFocused = it.isFocused } else Modifier,
            ),
        ) {
            Icon(
                imageVector = MuxGridIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (saving) "Saving…" else "Save & Connect",
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }

    val renderTestButton: @Composable (Modifier) -> Unit = { itemModifier ->
        val focused = isTv && testFocused
        val fg = if (focused) MuxGridColors.OnFocusBorder else MuxGridColors.TextPrimary
        OutlinedButton(
            onClick = onTest,
            enabled = !testing && !saving,
            shape = MuxGridShapes.Sm,
            contentPadding = btnPadding,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (focused) MuxGridColors.FocusBorder else MuxGridColors.CardSurfaceElevated,
                contentColor = fg,
            ),
            border = BorderStroke(
                if (focused) MuxGridBorders.Focused else MuxGridBorders.Hairline,
                if (focused) MuxGridColors.FocusBorder else MuxGridColors.HairlineBorder,
            ),
            modifier = itemModifier.then(
                if (isTv) Modifier.onFocusChanged { testFocused = it.isFocused } else Modifier,
            ),
        ) {
            Icon(
                imageVector = MuxGridIcons.Refresh,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (testing) "Testing…" else "Test",
                color = fg,
                maxLines = 1,
                softWrap = false,
            )
        }
    }

    if (isTv) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (canCancel) {
                val fg = if (cancelFocused) MuxGridColors.OnFocusBorder else MuxGridColors.TextSecondary
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !saving,
                    shape = MuxGridShapes.Sm,
                    contentPadding = btnPadding,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (cancelFocused) MuxGridColors.FocusBorder else MuxGridColors.CardSurfaceElevated,
                        contentColor = if (cancelFocused) MuxGridColors.OnFocusBorder else MuxGridColors.TextPrimary,
                    ),
                    border = BorderStroke(
                        if (cancelFocused) MuxGridBorders.Focused else MuxGridBorders.Hairline,
                        if (cancelFocused) MuxGridColors.FocusBorder else MuxGridColors.HairlineBorder,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { cancelFocused = it.isFocused },
                ) {
                    Icon(
                        imageVector = MuxGridIcons.Close,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Cancel",
                        color = fg,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            renderTestButton(Modifier.weight(1f))
            renderSaveButton(Modifier.weight(1.35f))
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            renderSaveButton(Modifier.fillMaxWidth())
            renderTestButton(Modifier.fillMaxWidth())
        }
    }
}

internal sealed interface ServerValidationResult {
    data class Valid(val profile: ServerProfile) : ServerValidationResult
    data class Invalid(val message: String) : ServerValidationResult
}

internal fun validateServerProfileInput(
    profileId: String,
    rawHost: String,
    rawHtspPort: String,
    rawHttpPort: String,
    rawUsername: String,
    password: String,
): ServerValidationResult {
    val hostValidation = validateServerHost(rawHost)
    if (hostValidation.error != null) {
        return ServerValidationResult.Invalid(hostValidation.error)
    }
    val cleanHost = hostValidation.normalizedHost!!
    val htspPort = rawHtspPort.trim().toIntOrNull()
    if (htspPort == null || htspPort !in 1..65535) {
        return ServerValidationResult.Invalid("Enter a valid HTSP port (default 9982)")
    }
    val httpPort = rawHttpPort.trim().toIntOrNull()
    if (httpPort == null || httpPort !in 1..65535) {
        return ServerValidationResult.Invalid("Enter a valid HTTP port (default 9981)")
    }
    return ServerValidationResult.Valid(
        ServerProfile(
            id = profileId,
            name = "Tvheadend ($cleanHost)",
            host = cleanHost,
            htspPort = htspPort,
            httpPort = httpPort,
            username = rawUsername.trim(),
            password = password,
        ),
    )
}

private data class HostValidation(
    val normalizedHost: String? = null,
    val error: String? = null,
)

private fun validateServerHost(rawHost: String): HostValidation {
    val trimmed = rawHost.trim()
    if (trimmed.isEmpty()) {
        return HostValidation(error = "Enter your Tvheadend server IP or hostname")
    }
    if (trimmed.any { it.isWhitespace() }) {
        return HostValidation(error = "Host or IP must not contain spaces")
    }
    if ("://" in trimmed || '/' in trimmed || '?' in trimmed || '#' in trimmed || '@' in trimmed) {
        return HostValidation(error = "Enter a hostname or IP without a URL scheme or path")
    }

    if ('[' in trimmed || ']' in trimmed) {
        if (!trimmed.startsWith('[') || !trimmed.endsWith(']')) {
            return HostValidation(error = "Invalid bracketed IPv6 address")
        }
        val inner = trimmed.substring(1, trimmed.length - 1)
        if ('[' in inner || ']' in inner || !isValidIpv6Literal(inner)) {
            return HostValidation(error = "Enter a valid IPv6 address")
        }
        return HostValidation(normalizedHost = inner)
    }

    if (':' in trimmed) {
        val colonCount = trimmed.count { it == ':' }
        if (colonCount == 1) {
            return HostValidation(error = "Enter the host or IP without a port number")
        }
        if (!isValidIpv6Literal(trimmed)) {
            return HostValidation(error = "Enter a valid IPv6 address")
        }
        return HostValidation(normalizedHost = trimmed)
    }

    if (trimmed.length > 253 || trimmed.startsWith('.') || trimmed.endsWith('.') || ".." in trimmed) {
        return HostValidation(error = "Enter a valid hostname or IP address")
    }

    val labels = trimmed.split('.')
    if (labels.size == 4 && labels.all { label -> label.isNotEmpty() && label.all { it.isDigit() } }) {
        val validIpv4 = labels.all { label ->
            label.length <= 3 && (label.toIntOrNull()?.let { it in 0..255 } == true)
        }
        return if (validIpv4) {
            HostValidation(normalizedHost = trimmed)
        } else {
            HostValidation(error = "Enter a valid IPv4 address")
        }
    }

    val validHostname = labels.all { label ->
        label.length in 1..63 &&
            label.first().isAsciiAlnum() &&
            label.last().isAsciiAlnum() &&
            label.all { it.isAsciiAlnum() || it == '-' }
    }
    return if (validHostname) {
        HostValidation(normalizedHost = trimmed)
    } else {
        HostValidation(error = "Enter a valid hostname or IP address")
    }
}

private fun Char.isAsciiAlnum(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

private fun isValidIpv6Literal(raw: String): Boolean {
    val pctIdx = raw.indexOf('%')
    val addrPart = if (pctIdx >= 0) {
        val zoneId = raw.substring(pctIdx + 1)
        if (zoneId.isEmpty() || zoneId.any { !it.isAsciiAlnum() && it != '.' && it != '_' && it != '-' }) {
            return false
        }
        raw.substring(0, pctIdx)
    } else {
        raw
    }

    if (addrPart.isEmpty() || ':' !in addrPart || ":::" in addrPart) return false
    val doubleColonIdx = addrPart.indexOf("::")
    if (doubleColonIdx >= 0 && addrPart.indexOf("::", doubleColonIdx + 2) >= 0) {
        return false
    }
    if (addrPart.startsWith(':') && !addrPart.startsWith("::")) return false
    if (addrPart.endsWith(':') && !addrPart.endsWith("::")) return false

    val parts = addrPart.split(':')
    var hextetCount = 0
    for (i in parts.indices) {
        val part = parts[i]
        if (part.isEmpty()) continue
        if ('.' in part) {
            if (i != parts.lastIndex) return false
            val ipv4Labels = part.split('.')
            if (ipv4Labels.size != 4 || ipv4Labels.any { l ->
                    l.isEmpty() || l.length > 3 || l.any { !it.isDigit() } || (l.toIntOrNull()?.let { it in 0..255 } != true)
                }
            ) {
                return false
            }
            hextetCount += 2
        } else {
            if (part.length > 4 || part.any { ch -> ch !in '0'..'9' && ch !in 'a'..'f' && ch !in 'A'..'F' }) {
                return false
            }
            hextetCount += 1
        }
    }
    return if (doubleColonIdx >= 0) hextetCount <= 7 else hextetCount == 8
}
