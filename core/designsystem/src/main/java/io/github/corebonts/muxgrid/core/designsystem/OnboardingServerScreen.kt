package io.github.corebonts.muxgrid.core.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.corebonts.muxgrid.core.model.DiagnosticSeverity
import io.github.corebonts.muxgrid.core.model.PortProbeResult
import io.github.corebonts.muxgrid.core.model.ServerDiagnosticsReport
import io.github.corebonts.muxgrid.core.model.ServerProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal enum class OnboardingStep(
    val number: Int,
    val label: String,
    val subtitle: String,
) {
    HOST(1, "Server", "Host or IP address"),
    PORTS(2, "Ports", "HTSP & HTTP ports"),
    CREDENTIALS(3, "Account", "Login & verification"),
}

private enum class StepBadgeState {
    ACTIVE,
    COMPLETED_OK,
    AUTO_DETECTED,
    WARNING,
    ERROR,
    PENDING,
}

@Composable
fun OnboardingServerScreen(
    initialProfile: ServerProfile,
    isTv: Boolean,
    canCancel: Boolean,
    onProbePorts: suspend (host: String, htspPort: Int, httpPort: Int) -> PortProbeResult,
    onRunDiagnostics: suspend (ServerProfile) -> ServerDiagnosticsReport,
    onSaveAndConnect: suspend (ServerProfile, ServerDiagnosticsReport?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val hostFocusRequester = remember { FocusRequester() }
    val htspPortFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }

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
    var passwordVisible by rememberSaveable(initialProfile.id) {
        mutableStateOf(false)
    }

    var activeStepName by rememberSaveable(initialProfile.id) {
        mutableStateOf(OnboardingStep.HOST.name)
    }
    val activeStep = remember(activeStepName) {
        runCatching { OnboardingStep.valueOf(activeStepName) }.getOrDefault(OnboardingStep.HOST)
    }
    var portsAutoDetected by rememberSaveable(initialProfile.id) {
        mutableStateOf(false)
    }
    var hostCompleted by rememberSaveable(initialProfile.id) {
        mutableStateOf(canCancel)
    }
    var portsUnlocked by rememberSaveable(initialProfile.id) {
        mutableStateOf(canCancel)
    }
    var credentialsUnlocked by rememberSaveable(initialProfile.id) {
        mutableStateOf(canCancel)
    }

    var probingPorts by remember { mutableStateOf(false) }
    var runningDiagnostics by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var activeProbeJob by remember { mutableStateOf<Job?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    var portProbeResult by remember { mutableStateOf<PortProbeResult?>(null) }
    var portProbeSignature by remember { mutableStateOf<String?>(null) }
    var diagnosticsReport by remember { mutableStateOf<ServerDiagnosticsReport?>(null) }
    var diagnosticsProfileSnapshot by remember { mutableStateOf<ServerProfile?>(null) }

    val isBusy = probingPorts || runningDiagnostics || saving
    val canCancelProbe = (probingPorts || runningDiagnostics) && !saving

    fun currentPortSignature(cleanHost: String, htspPort: Int, httpPort: Int): String =
        "$cleanHost:$htspPort:$httpPort"

    fun stepBack() {
        if (saving) return
        if (canCancelProbe) {
            activeProbeJob?.cancel()
            activeProbeJob = null
            probingPorts = false
            runningDiagnostics = false
            return
        }
        validationError = null
        when (activeStep) {
            OnboardingStep.HOST -> if (canCancel) onCancel()
            OnboardingStep.PORTS -> activeStepName = OnboardingStep.HOST.name
            OnboardingStep.CREDENTIALS -> {
                activeStepName = if (portsAutoDetected) {
                    OnboardingStep.HOST.name
                } else {
                    OnboardingStep.PORTS.name
                }
            }
        }
    }

    BackHandler(enabled = canCancelProbe || activeStep != OnboardingStep.HOST || canCancel) {
        stepBack()
    }

    LaunchedEffect(activeStep, isTv) {
        if (isTv) {
            when (activeStep) {
                OnboardingStep.HOST -> runCatching { hostFocusRequester.requestFocus() }
                OnboardingStep.PORTS -> runCatching { htspPortFocusRequester.requestFocus() }
                OnboardingStep.CREDENTIALS -> runCatching { usernameFocusRequester.requestFocus() }
            }
        }
    }

    fun validateHostOnly(): String? {
        val hostCheck = validateServerHost(host)
        if (hostCheck.error != null) {
            validationError = hostCheck.error
            return null
        }
        validationError = null
        return hostCheck.normalizedHost
    }

    fun validatePortsAndHost(): ServerProfile? {
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

    val activePortProbe = run {
        val cleanHost = validateServerHost(host).normalizedHost
        val htsp = htspPortText.trim().toIntOrNull()
        val http = httpPortText.trim().toIntOrNull()
        if (cleanHost != null && htsp != null && http != null &&
            portProbeSignature == currentPortSignature(cleanHost, htsp, http)
        ) {
            portProbeResult
        } else {
            null
        }
    }

    val activeDiagnostics = run {
        val currentProfile = (validateServerProfileInput(
            profileId = initialProfile.id,
            rawHost = host,
            rawHtspPort = htspPortText,
            rawHttpPort = httpPortText,
            rawUsername = username,
            password = password,
        ) as? ServerValidationResult.Valid)?.profile
        if (currentProfile != null && currentProfile == diagnosticsProfileSnapshot) {
            diagnosticsReport
        } else {
            null
        }
    }

    val onHostChanged: (String) -> Unit = { value ->
        if (host != value) {
            host = value
            validationError = null
            portsAutoDetected = false
            portProbeResult = null
            portProbeSignature = null
            diagnosticsReport = null
            diagnosticsProfileSnapshot = null
        }
    }

    val onHtspPortChanged: (String) -> Unit = { value ->
        if (htspPortText != value) {
            htspPortText = value
            validationError = null
            portsAutoDetected = false
            portProbeResult = null
            portProbeSignature = null
            diagnosticsReport = null
            diagnosticsProfileSnapshot = null
        }
    }

    val onHttpPortChanged: (String) -> Unit = { value ->
        if (httpPortText != value) {
            httpPortText = value
            validationError = null
            portsAutoDetected = false
            portProbeResult = null
            portProbeSignature = null
            diagnosticsReport = null
            diagnosticsProfileSnapshot = null
        }
    }

    val onUsernameChanged: (String) -> Unit = { value ->
        if (username != value) {
            username = value
            validationError = null
            diagnosticsReport = null
            diagnosticsProfileSnapshot = null
        }
    }

    val onPasswordChanged: (String) -> Unit = { value ->
        if (password != value) {
            password = value
            validationError = null
            diagnosticsReport = null
            diagnosticsProfileSnapshot = null
        }
    }

    fun launchPortProbe(
        hostToProbe: String,
        htspPort: Int,
        httpPort: Int,
        onProbeComplete: (PortProbeResult) -> Unit,
    ) {
        probingPorts = true
        val job = scope.launch {
            try {
                val probe = onProbePorts(hostToProbe, htspPort, httpPort)
                if (!isActive) return@launch
                portProbeResult = probe
                portProbeSignature = currentPortSignature(hostToProbe, htspPort, httpPort)
                onProbeComplete(probe)
            } finally {
                val thisJob = coroutineContext[Job]
                if (activeProbeJob == null || activeProbeJob === thisJob) {
                    probingPorts = false
                    activeProbeJob = null
                }
            }
        }
        activeProbeJob = job
    }

    fun launchDiagnosticsProbe(
        profile: ServerProfile,
        autoSaveIfClean: Boolean,
    ) {
        runningDiagnostics = true
        val job = scope.launch {
            try {
                val report = onRunDiagnostics(profile)
                if (!isActive) return@launch
                diagnosticsReport = report
                diagnosticsProfileSnapshot = profile
                if (autoSaveIfClean && !report.hasBlockers && !report.hasDegraded) {
                    runningDiagnostics = false
                    activeProbeJob = null
                    saving = true
                    try {
                        onSaveAndConnect(profile, report)
                    } finally {
                        saving = false
                    }
                }
            } finally {
                val thisJob = coroutineContext[Job]
                if (activeProbeJob == null || activeProbeJob === thisJob) {
                    runningDiagnostics = false
                    activeProbeJob = null
                }
            }
        }
        activeProbeJob = job
    }

    val advanceFromHost: () -> Unit = {
        if (!isBusy) {
            val cleanHost = validateHostOnly()
            if (cleanHost != null) {
                if (htspPortText.isBlank()) htspPortText = ServerProfile.DEFAULT_HTSP_PORT.toString()
                if (httpPortText.isBlank()) httpPortText = ServerProfile.DEFAULT_HTTP_PORT.toString()
                val htsp = htspPortText.trim().toIntOrNull()?.takeIf { it in 1..65535 }
                val http = httpPortText.trim().toIntOrNull()?.takeIf { it in 1..65535 }
                hostCompleted = true
                portsUnlocked = true

                if (htsp == null || http == null) {
                    portsAutoDetected = false
                    validationError = if (htsp == null) {
                        "Enter a valid HTSP port (default 9982)"
                    } else {
                        "Enter a valid HTTP port (default 9981)"
                    }
                    activeStepName = OnboardingStep.PORTS.name
                } else {
                    val sig = currentPortSignature(cleanHost, htsp, http)
                    val cachedProbe = if (portProbeSignature == sig) portProbeResult else null
                    if (cachedProbe != null) {
                        if (cachedProbe.bothReachable) {
                            portsAutoDetected = true
                            credentialsUnlocked = true
                            activeStepName = OnboardingStep.CREDENTIALS.name
                        } else {
                            portsAutoDetected = false
                            activeStepName = OnboardingStep.PORTS.name
                        }
                    } else {
                        launchPortProbe(cleanHost, htsp, http) { probe ->
                            if (probe.bothReachable) {
                                portsAutoDetected = true
                                credentialsUnlocked = true
                                activeStepName = OnboardingStep.CREDENTIALS.name
                            } else {
                                portsAutoDetected = false
                                activeStepName = OnboardingStep.PORTS.name
                            }
                        }
                    }
                }
            }
        }
    }

    val testPortsOnly: () -> Unit = {
        if (!isBusy) {
            val profile = validatePortsAndHost()
            if (profile != null) {
                launchPortProbe(profile.normalizedHost, profile.htspPort, profile.httpPort) { probe ->
                    portsAutoDetected = probe.bothReachable
                }
            }
        }
    }

    val advanceFromPorts: () -> Unit = {
        if (!isBusy) {
            val profile = validatePortsAndHost()
            if (profile != null) {
                hostCompleted = true
                portsUnlocked = true
                credentialsUnlocked = true
                val sig = currentPortSignature(profile.normalizedHost, profile.htspPort, profile.httpPort)
                if (portProbeSignature != sig) {
                    launchPortProbe(profile.normalizedHost, profile.htspPort, profile.httpPort) { probe ->
                        portsAutoDetected = probe.bothReachable
                        activeStepName = OnboardingStep.CREDENTIALS.name
                    }
                } else {
                    activeStepName = OnboardingStep.CREDENTIALS.name
                }
            }
        }
    }

    val runDiagnosticsOnly: () -> Unit = {
        if (!isBusy) {
            val profile = validatePortsAndHost()
            if (profile != null) {
                launchDiagnosticsProbe(profile, autoSaveIfClean = false)
            }
        }
    }

    val connectWithPreflight: () -> Unit = {
        if (!isBusy) {
            val profile = validatePortsAndHost()
            if (profile != null) {
                val cached = activeDiagnostics
                if (cached != null) {
                    if (!cached.hasBlockers) {
                        saving = true
                        scope.launch {
                            try {
                                onSaveAndConnect(profile, cached)
                            } finally {
                                saving = false
                            }
                        }
                    }
                } else {
                    launchDiagnosticsProbe(profile, autoSaveIfClean = true)
                }
            }
        }
    }

    val saveAnyway: () -> Unit = {
        if (!isBusy) {
            val profile = validatePortsAndHost()
            if (profile != null) {
                saving = true
                scope.launch {
                    try {
                        onSaveAndConnect(profile, activeDiagnostics)
                    } finally {
                        saving = false
                    }
                }
            }
        }
    }

    fun canSelectStep(step: OnboardingStep): Boolean {
        if (isBusy) return false
        return when (step) {
            OnboardingStep.HOST -> true
            OnboardingStep.PORTS -> canCancel || portsUnlocked || hostCompleted
            OnboardingStep.CREDENTIALS -> canCancel || credentialsUnlocked
        }
    }

    fun selectStep(step: OnboardingStep) {
        if (!canSelectStep(step)) return
        validationError = null
        activeStepName = step.name
    }

    fun stepBadgeState(step: OnboardingStep): StepBadgeState = when (step) {
        OnboardingStep.HOST -> when {
            activeStep == OnboardingStep.HOST -> StepBadgeState.ACTIVE
            hostCompleted && validateServerHost(host).error == null -> StepBadgeState.COMPLETED_OK
            else -> StepBadgeState.PENDING
        }
        OnboardingStep.PORTS -> when {
            activeStep == OnboardingStep.PORTS -> StepBadgeState.ACTIVE
            portsAutoDetected && activePortProbe?.bothReachable != false -> StepBadgeState.AUTO_DETECTED
            activePortProbe != null && activePortProbe.bothReachable -> StepBadgeState.COMPLETED_OK
            activePortProbe != null && !activePortProbe.htspReachable -> StepBadgeState.ERROR
            activePortProbe != null && !activePortProbe.httpReachable -> StepBadgeState.WARNING
            credentialsUnlocked -> StepBadgeState.COMPLETED_OK
            else -> StepBadgeState.PENDING
        }
        OnboardingStep.CREDENTIALS -> when {
            activeStep == OnboardingStep.CREDENTIALS -> StepBadgeState.ACTIVE
            activeDiagnostics?.hasBlockers == true -> StepBadgeState.ERROR
            activeDiagnostics?.hasDegraded == true -> StepBadgeState.WARNING
            activeDiagnostics != null -> StepBadgeState.COMPLETED_OK
            else -> StepBadgeState.PENDING
        }
    }

    fun stepSummaryText(step: OnboardingStep): String = when (step) {
        OnboardingStep.HOST -> host.trim().ifEmpty { step.subtitle }
        OnboardingStep.PORTS -> {
            val h = htspPortText.trim().ifEmpty { ServerProfile.DEFAULT_HTSP_PORT.toString() }
            val p = httpPortText.trim().ifEmpty { ServerProfile.DEFAULT_HTTP_PORT.toString() }
            when {
                portsAutoDetected -> "$h / $p · Detected"
                activePortProbe?.bothReachable == true -> "$h / $p · Reachable"
                activePortProbe != null -> "$h / $p · Check ports"
                else -> "$h / $p"
            }
        }
        OnboardingStep.CREDENTIALS -> when {
            activeDiagnostics != null -> activeDiagnostics.capabilitySummary
            username.isNotBlank() -> username.trim()
            credentialsUnlocked -> "Anonymous LAN"
            else -> step.subtitle
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(canvas)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        val useTwoPane = isTv || maxWidth >= 620.dp
        val cardMaxWidth = if (useTwoPane) 780.dp else 520.dp
        val outerHorizontalPad = when {
            isTv -> 48.dp
            useTwoPane -> 24.dp
            else -> 18.dp
        }

        Column(
            modifier = Modifier
                .padding(horizontal = outerHorizontalPad, vertical = 20.dp)
                .widthIn(max = cardMaxWidth)
                .fillMaxWidth()
                .clip(MuxGridShapes.Dialog)
                .background(MuxGridColors.CardSurface)
                .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Dialog),
        ) {
            // Top Wizard Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isTv) 24.dp else 20.dp,
                        vertical = if (isTv) 16.dp else 16.dp,
                    ),
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
                        modifier = Modifier.size(22.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (canCancel) "Tvheadend Server" else "Connect to Tvheadend",
                            style = MuxGridType.Title,
                            fontSize = if (isTv) 20.sp else 19.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "HTSP control & EPG paired with HTTP broadcast playback",
                            style = MuxGridType.CardSubtitle,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!isTv && canCancel) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(MuxGridShapes.Sm)
                            .clickable(enabled = !isBusy, onClick = onCancel),
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

            MuxGridDivider()

            if (useTwoPane) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Left Step Rail
                    Column(
                        modifier = Modifier
                            .width(if (isTv) 228.dp else 212.dp)
                            .padding(16.dp)
                            .focusGroup(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (step in OnboardingStep.entries) {
                            WizardStepRailItem(
                                step = step,
                                badgeState = stepBadgeState(step),
                                summary = stepSummaryText(step),
                                enabled = canSelectStep(step),
                                isTv = isTv,
                                onClick = { selectStep(step) },
                            )
                        }
                        if (isTv && canCancel) {
                            Spacer(Modifier.height(6.dp))
                            WizardButton(
                                label = "Cancel",
                                icon = MuxGridIcons.Close,
                                isPrimary = false,
                                isTv = true,
                                enabled = !isBusy,
                                onClick = onCancel,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Right Active Step Pane
                    val dividerColor = MuxGridColors.Divider
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .drawBehind {
                                val strokePx = MuxGridBorders.Divider.toPx()
                                drawLine(
                                    color = dividerColor,
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = strokePx,
                                )
                            }
                            .padding(if (isTv) 22.dp else 24.dp)
                            .focusGroup(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        WizardStepBody(
                            step = activeStep,
                            isTv = isTv,
                            useTwoPane = true,
                            host = host,
                            htspPortText = htspPortText,
                            httpPortText = httpPortText,
                            username = username,
                            password = password,
                            passwordVisible = passwordVisible,
                            onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                            onHostChanged = onHostChanged,
                            onHtspPortChanged = onHtspPortChanged,
                            onHttpPortChanged = onHttpPortChanged,
                            onUsernameChanged = onUsernameChanged,
                            onPasswordChanged = onPasswordChanged,
                            hostFocusRequester = hostFocusRequester,
                            htspPortFocusRequester = htspPortFocusRequester,
                            usernameFocusRequester = usernameFocusRequester,
                            fieldColors = fieldColors,
                            dpadFieldModifier = dpadFieldModifier,
                            validationError = validationError,
                            portProbe = activePortProbe,
                            diagnosticsReport = activeDiagnostics,
                            probingPorts = probingPorts,
                            runningDiagnostics = runningDiagnostics,
                            saving = saving,
                            portsAutoDetected = portsAutoDetected,
                            onBack = ::stepBack,
                            onAdvanceFromHost = advanceFromHost,
                            onTestPorts = testPortsOnly,
                            onAdvanceFromPorts = advanceFromPorts,
                            onTestConnection = runDiagnosticsOnly,
                            onConnect = connectWithPreflight,
                            onSaveAnyway = saveAnyway,
                        )
                    }
                }
            } else {
                // Portrait Mobile Accordion
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (step in OnboardingStep.entries) {
                        val isExpanded = activeStep == step
                        val badgeState = stepBadgeState(step)
                        val unlocked = canSelectStep(step)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MuxGridShapes.Card)
                                .background(
                                    if (isExpanded) MuxGridColors.CardSurfaceAlt else MuxGridColors.CardSurfaceElevated.copy(alpha = 0.45f),
                                )
                                .border(
                                    width = if (isExpanded) MuxGridBorders.Hairline else MuxGridBorders.Divider,
                                    color = if (isExpanded) MuxGridColors.Accent.copy(alpha = MuxGridAlphas.BorderSubtle) else MuxGridColors.HairlineBorder,
                                    shape = MuxGridShapes.Card,
                                ),
                        ) {
                            WizardAccordionHeader(
                                step = step,
                                badgeState = badgeState,
                                summary = stepSummaryText(step),
                                isExpanded = isExpanded,
                                enabled = unlocked,
                                onClick = { selectStep(step) },
                            )
                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    MuxGridDivider()
                                    WizardStepBody(
                                        step = step,
                                        isTv = false,
                                        useTwoPane = false,
                                        host = host,
                                        htspPortText = htspPortText,
                                        httpPortText = httpPortText,
                                        username = username,
                                        password = password,
                                        passwordVisible = passwordVisible,
                                        onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                                        onHostChanged = onHostChanged,
                                        onHtspPortChanged = onHtspPortChanged,
                                        onHttpPortChanged = onHttpPortChanged,
                                        onUsernameChanged = onUsernameChanged,
                                        onPasswordChanged = onPasswordChanged,
                                        hostFocusRequester = hostFocusRequester,
                                        htspPortFocusRequester = htspPortFocusRequester,
                                        usernameFocusRequester = usernameFocusRequester,
                                        fieldColors = fieldColors,
                                        dpadFieldModifier = Modifier,
                                        validationError = validationError,
                                        portProbe = activePortProbe,
                                        diagnosticsReport = activeDiagnostics,
                                        probingPorts = probingPorts,
                                        runningDiagnostics = runningDiagnostics,
                                        saving = saving,
                                        portsAutoDetected = portsAutoDetected,
                                        onBack = ::stepBack,
                                        onAdvanceFromHost = advanceFromHost,
                                        onTestPorts = testPortsOnly,
                                        onAdvanceFromPorts = advanceFromPorts,
                                        onTestConnection = runDiagnosticsOnly,
                                        onConnect = connectWithPreflight,
                                        onSaveAnyway = saveAnyway,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardStepRailItem(
    step: OnboardingStep,
    badgeState: StepBadgeState,
    summary: String,
    enabled: Boolean,
    isTv: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val isActive = badgeState == StepBadgeState.ACTIVE
    val containerColor = when {
        isTv && focused -> MuxGridColors.FocusBorder
        isActive -> MuxGridColors.CardSurfaceElevated
        else -> Color.Transparent
    }
    val titleColor = when {
        isTv && focused -> MuxGridColors.OnFocusBorder
        isActive -> MuxGridColors.TextPrimary
        enabled -> MuxGridColors.TextSecondary
        else -> MuxGridColors.TextMuted
    }
    val subColor = when {
        isTv && focused -> MuxGridColors.OnFocusBorder.copy(alpha = 0.8f)
        else -> MuxGridColors.TextMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Row)
            .background(containerColor)
            .border(
                width = if (isTv && focused) MuxGridBorders.Focused else if (isActive) MuxGridBorders.Hairline else 0.dp,
                color = when {
                    isTv && focused -> MuxGridColors.FocusBorder
                    isActive -> MuxGridColors.Accent.copy(alpha = MuxGridAlphas.BorderEmphasis)
                    else -> Color.Transparent
                },
                shape = MuxGridShapes.Row,
            )
            .onFocusChanged { if (focused != it.isFocused) focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WizardStepBadge(stepNumber = step.number, state = badgeState)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = step.label,
                fontSize = 13.5.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                fontSize = 11.5.sp,
                color = subColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WizardAccordionHeader(
    step: OnboardingStep,
    badgeState: StepBadgeState,
    summary: String,
    isExpanded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !isExpanded, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WizardStepBadge(stepNumber = step.number, state = badgeState)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = step.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled || isExpanded) MuxGridColors.TextPrimary else MuxGridColors.TextMuted,
            )
            if (!isExpanded) {
                Text(
                    text = summary,
                    style = MuxGridType.TabularSmall,
                    color = if (badgeState == StepBadgeState.AUTO_DETECTED) MuxGridColors.EmeraldOk else MuxGridColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = if (isExpanded) MuxGridIcons.ExpandLess else MuxGridIcons.ExpandMore,
            contentDescription = null,
            tint = if (enabled) MuxGridColors.TextSecondary else MuxGridColors.TextMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun WizardStepBadge(
    stepNumber: Int,
    state: StepBadgeState,
) {
    val (bg, fg, icon) = when (state) {
        StepBadgeState.ACTIVE -> Triple(MuxGridColors.Accent, MuxGridColors.OnAccent, null)
        StepBadgeState.COMPLETED_OK, StepBadgeState.AUTO_DETECTED -> Triple(
            MuxGridColors.EmeraldOk.copy(alpha = MuxGridAlphas.SubtleFill),
            MuxGridColors.EmeraldOk,
            MuxGridIcons.Check,
        )
        StepBadgeState.WARNING -> Triple(
            MuxGridColors.AmberWarn.copy(alpha = MuxGridAlphas.SubtleFill),
            MuxGridColors.AmberWarn,
            MuxGridIcons.Warning,
        )
        StepBadgeState.ERROR -> Triple(
            MuxGridColors.CoralLive.copy(alpha = MuxGridAlphas.SubtleFill),
            MuxGridColors.CoralLive,
            MuxGridIcons.Close,
        )
        StepBadgeState.PENDING -> Triple(
            MuxGridColors.CardSurfaceElevated,
            MuxGridColors.TextMuted,
            null,
        )
    }
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(MuxGridShapes.Pill)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
        } else {
            Text(
                text = stepNumber.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
            )
        }
    }
}

@Composable
private fun WizardStepBody(
    step: OnboardingStep,
    isTv: Boolean,
    useTwoPane: Boolean,
    host: String,
    htspPortText: String,
    httpPortText: String,
    username: String,
    password: String,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    onHostChanged: (String) -> Unit,
    onHtspPortChanged: (String) -> Unit,
    onHttpPortChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    hostFocusRequester: FocusRequester,
    htspPortFocusRequester: FocusRequester,
    usernameFocusRequester: FocusRequester,
    fieldColors: TextFieldColors,
    dpadFieldModifier: Modifier,
    validationError: String?,
    portProbe: PortProbeResult?,
    diagnosticsReport: ServerDiagnosticsReport?,
    probingPorts: Boolean,
    runningDiagnostics: Boolean,
    saving: Boolean,
    portsAutoDetected: Boolean,
    onBack: () -> Unit,
    onAdvanceFromHost: () -> Unit,
    onTestPorts: () -> Unit,
    onAdvanceFromPorts: () -> Unit,
    onTestConnection: () -> Unit,
    onConnect: () -> Unit,
    onSaveAnyway: () -> Unit,
) {
    when (step) {
        OnboardingStep.HOST -> {
            Text(
                text = "Where is Tvheadend running?",
                style = MuxGridType.CardTitle,
                fontSize = 15.sp,
            )
            Text(
                text = "Enter your server IP address or hostname. Standard ports (9982 / 9981) are detected automatically.",
                style = MuxGridType.CardSubtitle,
            )
            OutlinedTextField(
                value = host,
                onValueChange = onHostChanged,
                label = { Text("Server Host / IP") },
                placeholder = { Text("tvheadend.local or 192.168.1.10") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { onAdvanceFromHost() }),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(hostFocusRequester)
                    .then(dpadFieldModifier),
            )
            validationError?.let { err ->
                Text(text = err, color = MuxGridColors.CoralLive, fontSize = 12.5.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                WizardButton(
                    label = if (probingPorts) "Detecting Ports…" else "Next",
                    icon = if (probingPorts) null else MuxGridIcons.ChevronRight,
                    trailingIcon = !probingPorts,
                    isPrimary = true,
                    isTv = isTv,
                    enabled = !probingPorts && !saving,
                    loading = probingPorts,
                    onClick = onAdvanceFromHost,
                    modifier = if (useTwoPane) Modifier else Modifier.fillMaxWidth(),
                )
            }
        }

        OnboardingStep.PORTS -> {
            Text(
                text = "Server Ports",
                style = MuxGridType.CardTitle,
                fontSize = 15.sp,
            )
            Text(
                text = if (portProbe != null && !portProbe.bothReachable) {
                    "One or more default ports did not respond. Verify your Tvheadend HTSP and HTTP ports."
                } else {
                    "Standard Tvheadend ports are 9982 (HTSP) and 9981 (HTTP)."
                },
                style = MuxGridType.CardSubtitle,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = htspPortText,
                    onValueChange = onHtspPortChanged,
                    label = { Text("HTSP Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    colors = fieldColors,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(htspPortFocusRequester)
                        .then(dpadFieldModifier),
                )
                OutlinedTextField(
                    value = httpPortText,
                    onValueChange = onHttpPortChanged,
                    label = { Text("HTTP Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onAdvanceFromPorts() }),
                    colors = fieldColors,
                    modifier = Modifier
                        .weight(1f)
                        .then(dpadFieldModifier),
                )
            }

            if (portProbe != null) {
                PortProbeBadgesRow(portProbe)
            }

            validationError?.let { err ->
                Text(text = err, color = MuxGridColors.CoralLive, fontSize = 12.5.sp)
            }

            if (useTwoPane) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WizardButton(
                        label = "Back",
                        icon = MuxGridIcons.ChevronLeft,
                        isPrimary = false,
                        isTv = isTv,
                        enabled = !probingPorts && !saving,
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    WizardButton(
                        label = if (probingPorts) "Probing…" else "Test Ports",
                        icon = if (probingPorts) null else MuxGridIcons.Refresh,
                        isPrimary = false,
                        isTv = isTv,
                        enabled = !probingPorts && !saving,
                        loading = probingPorts,
                        onClick = onTestPorts,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(24.dp))
                    WizardButton(
                        label = "Next",
                        icon = MuxGridIcons.ChevronRight,
                        trailingIcon = true,
                        isPrimary = true,
                        isTv = isTv,
                        enabled = !probingPorts && !saving,
                        onClick = onAdvanceFromPorts,
                        modifier = Modifier.weight(1.6f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        WizardButton(
                            label = "Back",
                            icon = MuxGridIcons.ChevronLeft,
                            isPrimary = false,
                            isTv = false,
                            enabled = !probingPorts && !saving,
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                        )
                        WizardButton(
                            label = if (probingPorts) "Probing…" else "Test Ports",
                            icon = if (probingPorts) null else MuxGridIcons.Refresh,
                            isPrimary = false,
                            isTv = false,
                            enabled = !probingPorts && !saving,
                            loading = probingPorts,
                            onClick = onTestPorts,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    WizardButton(
                        label = "Next",
                        icon = MuxGridIcons.ChevronRight,
                        trailingIcon = true,
                        isPrimary = true,
                        isTv = false,
                        enabled = !probingPorts && !saving,
                        onClick = onAdvanceFromPorts,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        OnboardingStep.CREDENTIALS -> {
            Text(
                text = "Credentials & Preflight",
                style = MuxGridType.CardTitle,
                fontSize = 15.sp,
            )
            Text(
                text = buildString {
                    append("Leave blank if Tvheadend allows anonymous LAN access.")
                    if (portsAutoDetected) {
                        append(" Ports ${htspPortText.trim()}/${httpPortText.trim()} detected.")
                    }
                },
                style = MuxGridType.CardSubtitle,
            )

            if (isTv) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChanged,
                        label = { Text("Username") },
                        placeholder = { Text("muxgrid") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = fieldColors,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(usernameFocusRequester)
                            .then(dpadFieldModifier),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChanged,
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onConnect() }),
                        colors = fieldColors,
                        modifier = Modifier
                            .weight(1f)
                            .then(dpadFieldModifier),
                    )
                }
            } else {
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChanged,
                    label = { Text("Username") },
                    placeholder = { Text("muxgrid") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(usernameFocusRequester),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChanged,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = onTogglePasswordVisible) {
                            Icon(
                                imageVector = if (passwordVisible) MuxGridIcons.VisibilityOff else MuxGridIcons.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = MuxGridColors.TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConnect() }),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            validationError?.let { err ->
                Text(text = err, color = MuxGridColors.CoralLive, fontSize = 12.5.sp)
            }

            diagnosticsReport?.let { report ->
                ServerDiagnosticsSummaryCard(report = report)
            }

            val hasBlockers = diagnosticsReport?.hasBlockers == true
            val hasDegraded = diagnosticsReport?.hasDegraded == true && !hasBlockers
            val primaryLabel = when {
                saving -> "Saving…"
                runningDiagnostics -> "Checking…"
                hasDegraded -> "Continue Anyway"
                else -> "Save & Connect"
            }

            if (useTwoPane) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WizardButton(
                        label = "Back",
                        icon = MuxGridIcons.ChevronLeft,
                        isPrimary = false,
                        isTv = isTv,
                        enabled = !runningDiagnostics && !saving,
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    WizardButton(
                        label = if (diagnosticsReport != null) "Retest" else "Test",
                        icon = if (runningDiagnostics) null else MuxGridIcons.Refresh,
                        isPrimary = hasBlockers,
                        isTv = isTv,
                        enabled = !runningDiagnostics && !saving,
                        loading = runningDiagnostics,
                        onClick = onTestConnection,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(24.dp))
                    if (hasBlockers) {
                        WizardButton(
                            label = "Save Anyway",
                            icon = null,
                            isPrimary = false,
                            isTv = isTv,
                            enabled = !runningDiagnostics && !saving,
                            onClick = onSaveAnyway,
                            modifier = Modifier.weight(1.6f),
                        )
                    } else {
                        WizardButton(
                            label = primaryLabel,
                            icon = if (saving || runningDiagnostics) null else MuxGridIcons.Check,
                            isPrimary = true,
                            isTv = isTv,
                            enabled = !runningDiagnostics && !saving,
                            loading = saving || runningDiagnostics,
                            onClick = onConnect,
                            modifier = Modifier.weight(1.6f),
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        WizardButton(
                            label = "Back",
                            icon = MuxGridIcons.ChevronLeft,
                            isPrimary = false,
                            isTv = false,
                            enabled = !runningDiagnostics && !saving,
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                        )
                        WizardButton(
                            label = if (diagnosticsReport != null) "Retest" else "Test",
                            icon = if (runningDiagnostics) null else MuxGridIcons.Refresh,
                            isPrimary = hasBlockers,
                            isTv = false,
                            enabled = !runningDiagnostics && !saving,
                            loading = runningDiagnostics,
                            onClick = onTestConnection,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (hasBlockers) {
                        WizardButton(
                            label = "Save Anyway",
                            icon = null,
                            isPrimary = false,
                            isTv = false,
                            enabled = !runningDiagnostics && !saving,
                            onClick = onSaveAnyway,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        WizardButton(
                            label = primaryLabel,
                            icon = if (saving || runningDiagnostics) null else MuxGridIcons.Check,
                            isPrimary = true,
                            isTv = false,
                            enabled = !runningDiagnostics && !saving,
                            loading = saving || runningDiagnostics,
                            onClick = onConnect,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortProbeBadgesRow(probe: PortProbeResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Sm)
            .background(MuxGridColors.CardSurfaceElevated.copy(alpha = 0.7f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PortStatusChip(
                label = "HTSP ${probe.htspPort}",
                reachable = probe.htspReachable,
                isBlocker = true,
                modifier = Modifier.weight(1f),
            )
            PortStatusChip(
                label = "HTTP ${probe.httpPort}",
                reachable = probe.httpReachable,
                isBlocker = false,
                modifier = Modifier.weight(1f),
            )
        }
        val detail = when {
            probe.bothReachable -> listOfNotNull(probe.serverName, probe.serverVersion)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .let { if (it.isNotEmpty()) "Detected $it" else "Both ports responded" }
            !probe.htspReachable && !probe.httpReachable ->
                probe.htspError ?: "Neither port responded on ${probe.host}"
            !probe.htspReachable ->
                probe.htspError ?: "HTSP port ${probe.htspPort} did not respond"
            else ->
                "HTTP port ${probe.httpPort} did not respond (Live TV playback needs HTTP)"
        }
        Text(
            text = detail,
            fontSize = 11.5.sp,
            color = MuxGridColors.TextSecondary,
        )
    }
}

@Composable
private fun PortStatusChip(
    label: String,
    reachable: Boolean,
    isBlocker: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = when {
        reachable -> MuxGridColors.EmeraldOk
        isBlocker -> MuxGridColors.CoralLive
        else -> MuxGridColors.AmberWarn
    }
    Row(
        modifier = modifier
            .clip(MuxGridShapes.Badge)
            .background(color.copy(alpha = MuxGridAlphas.SubtleFill))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (reachable) MuxGridIcons.CheckCircle else MuxGridIcons.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = if (reachable) "$label · OK" else "$label · Unreachable",
            color = color,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WizardButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    isPrimary: Boolean,
    isTv: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: Boolean = false,
    loading: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val padding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    val sizedModifier = modifier.height(42.dp)
    if (isPrimary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = MuxGridShapes.Sm,
            contentPadding = padding,
            border = if (isTv && focused) BorderStroke(MuxGridBorders.Focused, MuxGridColors.FocusBorder) else null,
            colors = ButtonDefaults.buttonColors(
                containerColor = MuxGridColors.Accent,
                contentColor = MuxGridColors.OnAccent,
            ),
            modifier = sizedModifier.then(
                if (isTv) Modifier.onFocusChanged { focused = it.isFocused } else Modifier,
            ),
        ) {
            WizardButtonContent(label, icon, trailingIcon, loading, MuxGridColors.OnAccent)
        }
    } else {
        val fg = if (isTv && focused) MuxGridColors.OnFocusBorder else MuxGridColors.TextPrimary
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = MuxGridShapes.Sm,
            contentPadding = padding,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (isTv && focused) MuxGridColors.FocusBorder else MuxGridColors.CardSurfaceElevated,
                contentColor = fg,
            ),
            border = BorderStroke(
                if (isTv && focused) MuxGridBorders.Focused else MuxGridBorders.Hairline,
                if (isTv && focused) MuxGridColors.FocusBorder else MuxGridColors.HairlineBorder,
            ),
            modifier = sizedModifier.then(
                if (isTv) Modifier.onFocusChanged { focused = it.isFocused } else Modifier,
            ),
        ) {
            WizardButtonContent(label, icon, trailingIcon, loading, fg)
        }
    }
}

@Composable
private fun WizardButtonContent(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    trailingIcon: Boolean,
    loading: Boolean,
    tint: Color,
) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = tint,
        )
        Spacer(Modifier.width(6.dp))
    } else if (icon != null && !trailingIcon) {
        WizardButtonIcon(icon = icon, tint = tint)
        Spacer(Modifier.width(6.dp))
    }
    Text(
        text = label,
        color = tint,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        maxLines = 1,
        softWrap = false,
    )
    if (!loading && icon != null && trailingIcon) {
        Spacer(Modifier.width(6.dp))
        WizardButtonIcon(icon = icon, tint = tint)
    }
}

@Composable
private fun WizardButtonIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
) {
    val isChevron = icon === MuxGridIcons.ChevronLeft || icon === MuxGridIcons.ChevronRight
    if (isChevron) {
        Box(
            modifier = Modifier.size(width = 8.dp, height = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.requiredSize(16.dp),
            )
        }
    } else {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
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
