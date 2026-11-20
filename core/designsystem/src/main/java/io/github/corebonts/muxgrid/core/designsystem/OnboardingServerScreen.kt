package io.github.corebonts.muxgrid.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    var host by remember(initialProfile) { mutableStateOf(initialProfile.host) }
    var htspPortText by remember(initialProfile) { mutableStateOf(initialProfile.htspPort.toString()) }
    var httpPortText by remember(initialProfile) { mutableStateOf(initialProfile.httpPort.toString()) }
    var username by remember(initialProfile) { mutableStateOf(initialProfile.username) }
    var password by remember(initialProfile) { mutableStateOf(initialProfile.password) }

    var testing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ConnectionTestResult?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    var testFocused by remember { mutableStateOf(false) }
    var saveFocused by remember { mutableStateOf(false) }
    var cancelFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isTv) {
            runCatching { hostFocusRequester.requestFocus() }
        }
    }

    fun buildProfile(): ServerProfile? {
        val cleanHost = host.trim()
        val htspPort = htspPortText.trim().toIntOrNull()
        val httpPort = httpPortText.trim().toIntOrNull()
        if (cleanHost.isEmpty()) {
            validationError = "Enter your Tvheadend server IP or hostname"
            return null
        }
        if (htspPort == null || htspPort !in 1..65535) {
            validationError = "Enter a valid HTSP port (default 9982)"
            return null
        }
        if (httpPort == null || httpPort !in 1..65535) {
            validationError = "Enter a valid HTTP port (default 9981)"
            return null
        }
        validationError = null
        return ServerProfile(
            id = initialProfile.id,
            name = "Tvheadend ($cleanHost)",
            host = cleanHost,
            htspPort = htspPort,
            httpPort = httpPort,
            username = username.trim(),
            password = password,
        )
    }

    val canvas = if (isTv) MuxGridColors.CanvasTv else MuxGridColors.CanvasPhone
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MuxGridColors.FocusBorder,
        unfocusedBorderColor = MuxGridColors.HairlineBorder,
        focusedTextColor = MuxGridColors.TextPrimary,
        unfocusedTextColor = MuxGridColors.TextPrimary,
        focusedLabelColor = MuxGridColors.AccentBlueLight,
        unfocusedLabelColor = MuxGridColors.TextSecondary,
        cursorColor = MuxGridColors.AccentBlueLight,
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
                .clip(MuxGridShapes.Sheet)
                .background(MuxGridColors.CardSurface)
                .border(MuxGridBorders.Hairline, MuxGridColors.HairlineBorder, MuxGridShapes.Sheet)
                .verticalScroll(rememberScrollState())
                .padding(if (isTv) 20.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 14.dp),
        ) {
            Text(
                text = "Connect to Tvheadend",
                style = MuxGridType.Title,
                fontSize = if (isTv) 20.sp else 22.sp,
            )
            Text(
                text = "Uses HTSP (control, EPG, auth) and HTTP pass-through for instant broadcast playback.",
                style = MuxGridType.CardSubtitle,
            )

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Server Host / IP") },
                placeholder = { Text("192.168.1.5") },
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

            if (isTv) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        placeholder = { Text("androidclient") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f).then(dpadFieldModifier),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val profile = buildProfile() ?: return@KeyboardActions
                                saving = true
                                scope.launch {
                                    onSaveAndConnect(profile)
                                    saving = false
                                }
                            },
                        ),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f).then(dpadFieldModifier),
                    )
                }
            } else {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    placeholder = { Text("androidclient") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(dpadFieldModifier),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val profile = buildProfile() ?: return@KeyboardActions
                            saving = true
                            scope.launch {
                                onSaveAndConnect(profile)
                                saving = false
                            }
                        },
                    ),
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(dpadFieldModifier),
                )
            }

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
                        MuxGridColors.EmeraldOk.copy(alpha = 0.14f),
                        MuxGridColors.EmeraldOk,
                        "Connected to ${res.serverName} ${res.serverVersion} (HTSP v${res.htspVersion}, DVR=${res.hasDvrAccess})",
                    )
                    is ConnectionTestResult.Failure -> Triple(
                        MuxGridColors.CoralLive.copy(alpha = 0.14f),
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (canCancel) {
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (cancelFocused) MuxGridColors.FocusBorder else MuxGridColors.CardSurface,
                            contentColor = if (cancelFocused) MuxGridColors.CanvasTv else MuxGridColors.TextPrimary,
                        ),
                        border = BorderStroke(
                            if (cancelFocused) 2.dp else 1.dp,
                            if (cancelFocused) MuxGridColors.FocusBorder else MuxGridColors.HairlineBorder,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { cancelFocused = it.isFocused },
                    ) {
                        Text(
                            text = "Cancel",
                            color = if (cancelFocused) MuxGridColors.CanvasTv else MuxGridColors.TextSecondary,
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        if (testing || saving) return@OutlinedButton
                        val profile = buildProfile() ?: return@OutlinedButton
                        testing = true
                        testResult = null
                        scope.launch {
                            testResult = onTestConnection(profile)
                            testing = false
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (testFocused) MuxGridColors.FocusBorder else MuxGridColors.CardSurface,
                        contentColor = if (testFocused) MuxGridColors.CanvasTv else MuxGridColors.TextPrimary,
                    ),
                    border = BorderStroke(
                        if (testFocused) 2.dp else 1.dp,
                        if (testFocused) MuxGridColors.FocusBorder else MuxGridColors.HairlineBorder,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { testFocused = it.isFocused },
                ) {
                    Text(
                        text = if (testing) "Testing…" else "Test",
                        color = if (testFocused) MuxGridColors.CanvasTv else MuxGridColors.TextPrimary,
                    )
                }
                Button(
                    onClick = {
                        if (saving) return@Button
                        val profile = buildProfile() ?: return@Button
                        saving = true
                        scope.launch {
                            onSaveAndConnect(profile)
                            saving = false
                        }
                    },
                    border = if (saveFocused) BorderStroke(2.dp, MuxGridColors.FocusBorder) else null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MuxGridColors.AccentBlue,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .weight(1.3f)
                        .onFocusChanged { saveFocused = it.isFocused },
                ) {
                    Text(if (saving) "Saving…" else "Save & Connect", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
