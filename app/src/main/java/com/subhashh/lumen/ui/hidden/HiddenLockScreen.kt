package com.subhashh.lumen.ui.hidden

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.R
import com.subhashh.lumen.data.HiddenLockStore
import com.subhashh.lumen.data.LockType
import com.subhashh.lumen.ui.components.pressable
import com.subhashh.lumen.ui.icons.LumenIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Why the lock screen is up. */
/**
 * Setup: choose a new lock. Unlock: enter it (fingerprint or face offered). Change: enter the
 * current one, then choose a new one. Confirm: enter it with no fingerprint shortcut, when the
 * lock itself is needed (it seals the vault's recovery key).
 */
enum class LockFlow { Setup, Unlock, Change, Confirm }

private sealed interface Step {
    data object Choose : Step
    /** Enter the current secret (unlock, or before changing it). */
    data class Verify(val then: LockFlow) : Step
    data class New(val type: LockType) : Step
    data class Confirm(val type: LockType, val first: String) : Step
    data object Biometrics : Step
}

private val Accent = Color(0xFF0A84FF)
private val Danger = Color(0xFFFF453A)

/**
 * Hidden's own lock: set up a PIN, pattern or password (entered twice), optionally add
 * fingerprint/face, and unlock with them. Full-screen and opaque so nothing behind shows through.
 */
@Composable
fun HiddenLockScreen(
    flow: LockFlow,
    store: HiddenLockStore,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onForgot: () -> Unit,
    /** What the lock guards, for its wording: "Hidden", or "Lumen" for App Lock. */
    lockedName: String = stringResource(R.string.hidden_name),
    /** The lock itself, whenever it's entered correctly or newly set (never for fingerprint or face). */
    onSecret: (String) -> Unit = {},
) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var step by remember(flow) {
        mutableStateOf(if (flow == LockFlow.Setup) Step.Choose else Step.Verify(then = flow))
    }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    // Bumped to clear an entry after a wrong or mismatched try.
    var attempt by remember { mutableStateOf(0) }

    BackHandler(onBack = onCancel)

    fun fail(message: String) {
        error = message
        attempt++
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
        scope.launch {
            shake.animateTo(0f, keyframes {
                durationMillis = 360
                -14f at 50; 14f at 110; -10f at 170; 10f at 230; -4f at 290
            })
        }
    }

    fun submit(secret: String) {
        if (busy) return
        when (val s = step) {
            is Step.Verify -> {
                val wait = store.lockedOutUntil() - System.currentTimeMillis()
                if (wait > 0) return fail(context.getString(R.string.hidden_too_many_tries, (wait / 1000) + 1))
                busy = true
                scope.launch {
                    // PBKDF2 is deliberately slow; keep it off the main thread.
                    val ok = withContext(Dispatchers.Default) { store.verify(secret) }
                    busy = false
                    if (ok) {
                        store.recordSuccess()
                        error = null
                        onSecret(secret)
                        if (s.then == LockFlow.Change) step = Step.Choose else onDone()
                    } else {
                        store.recordFailure()
                        val left = HiddenLockStore.MaxAttempts - store.failedAttempts() % HiddenLockStore.MaxAttempts
                        fail(
                            if (left == HiddenLockStore.MaxAttempts) context.getString(R.string.hidden_too_many_tries, 30)
                            else context.getString(sentence(store.type, R.string.hidden_wrong_pin, R.string.hidden_wrong_pattern, R.string.hidden_wrong_password, R.string.hidden_wrong_entry)),
                        )
                    }
                }
            }
            is Step.New -> {
                val problem = when (s.type) {
                    LockType.Pin -> if (secret.length !in 4..8) context.getString(R.string.hidden_pin_rule) else null
                    LockType.Pattern -> if (secret.length < 4) context.getString(R.string.hidden_pattern_rule) else null
                    LockType.Password -> if (secret.length < 4) context.getString(R.string.hidden_password_rule) else null
                }
                if (problem != null) return fail(problem)
                error = null
                step = Step.Confirm(s.type, secret)
            }
            is Step.Confirm -> {
                if (secret != s.first) {
                    step = Step.New(s.type)
                    return fail(context.getString(R.string.hidden_mismatch))
                }
                busy = true
                scope.launch {
                    withContext(Dispatchers.Default) { store.set(s.type, secret) }
                    store.recordSuccess()
                    onSecret(secret)
                    busy = false
                    error = null
                    if (activity != null && HiddenLock.biometricsAvailable(activity)) step = Step.Biometrics else onDone()
                }
            }
            else -> Unit
        }
    }

    // Unlocking with biometrics on: offer them straight away, with the Lumen lock as fallback.
    LaunchedEffect(flow) {
        val type = store.type
        if (flow == LockFlow.Unlock && store.biometrics && type != null &&
            activity != null && HiddenLock.biometricsAvailable(activity)
        ) {
            delay(250) // let the screen settle in first
            HiddenLock.biometric(activity, activity.getString(R.string.hidden_use_lock, type.localName(activity)), onSuccess = {
                store.recordSuccess()
                onDone()
            }, onFallback = {}, title = activity.getString(R.string.hidden_unlock_named, lockedName))
        }
    }

    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            // Opaque to touches too: nothing behind the lock may react.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.common_cancel),
                color = Accent,
                fontSize = 17.sp,
                modifier = Modifier
                    .align(Alignment.Start)
                    .clickable(onClick = onCancel)
                    .padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(20.dp))
            Icon(LumenIcons.Lock, null, tint = Accent, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(14.dp))
            val (title, subtitle) = titles(step, store, flow, lockedName)
            Text(title, color = colors.onBackground, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                error ?: subtitle,
                color = if (error != null) Danger else colors.onBackground.copy(alpha = 0.6f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .offset { IntOffset(shake.value.roundToInt(), 0) },
                contentAlignment = Alignment.TopCenter,
            ) {
                when (val s = step) {
                    Step.Choose -> ChooseType { step = Step.New(it) }
                    Step.Biometrics -> BiometricsOffer(
                        onEnable = {
                            store.biometrics = true
                            onDone()
                        },
                        onSkip = {
                            store.biometrics = false
                            onDone()
                        },
                    )
                    else -> {
                        val type = when (s) {
                            is Step.New -> s.type
                            is Step.Confirm -> s.type
                            else -> store.type ?: LockType.Pin
                        }
                        val autoLength = if (s is Step.Verify) store.pinLength else 0
                        // A fresh entry per step and per try.
                        androidx.compose.runtime.key(s, attempt) {
                            when (type) {
                                LockType.Pin -> PinEntry(autoLength, ::submit)
                                LockType.Pattern -> PatternEntry(::submit)
                                LockType.Password -> PasswordEntry(stringResource(if (s is Step.Verify) R.string.hidden_unlock else R.string.common_next), ::submit)
                            }
                        }
                    }
                }
            }

            if (step is Step.Verify) {
                Text(
                    stringResource(sentence(store.type, R.string.hidden_forgot_pin, R.string.hidden_forgot_pattern, R.string.hidden_forgot_password, R.string.hidden_forgot_it)),
                    color = Accent,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clickable(onClick = onForgot)
                        .padding(12.dp),
                )
            }
        }
    }
}

/** A lock type's name in the app's language; [LockType.label] is English only. */
/**
 * One whole sentence per lock type (and one for no type yet), never a lock name dropped into a
 * sentence: in many languages the words around it change with it.
 */
private fun sentence(type: LockType?, pin: Int, pattern: Int, password: Int, none: Int): Int = when (type) {
    LockType.Pin -> pin
    LockType.Pattern -> pattern
    LockType.Password -> password
    null -> none
}

private fun LockType.localName(context: android.content.Context): String = context.getString(
    when (this) {
        LockType.Pin -> R.string.hidden_lock_pin
        LockType.Pattern -> R.string.hidden_lock_pattern
        LockType.Password -> R.string.hidden_lock_password
    },
)

@Composable
private fun titles(step: Step, store: HiddenLockStore, flow: LockFlow, lockedName: String): Pair<String, String> {
    val context = LocalContext.current
    return when (step) {
        Step.Choose -> stringResource(if (flow == LockFlow.Change) R.string.hidden_new_lock else R.string.hidden_lock_hidden) to
            stringResource(R.string.hidden_choose_subtitle)
        is Step.Verify -> stringResource(sentence(store.type, R.string.hidden_enter_pin, R.string.hidden_enter_pattern, R.string.hidden_enter_password, R.string.hidden_enter_pin)) to
            when (step.then) {
                LockFlow.Change -> stringResource(R.string.hidden_enter_current)
                LockFlow.Confirm -> stringResource(R.string.hidden_confirm_you)
                else -> stringResource(R.string.hidden_is_locked, lockedName)
            }
        is Step.New -> when (step.type) {
            LockType.Pin -> stringResource(R.string.hidden_create_pin) to stringResource(R.string.hidden_pin_length)
            LockType.Pattern -> stringResource(R.string.hidden_draw_pattern) to stringResource(R.string.hidden_pattern_rule)
            LockType.Password -> stringResource(R.string.hidden_create_password) to stringResource(R.string.hidden_password_length)
        }
        is Step.Confirm -> stringResource(R.string.hidden_confirm_type, step.type.localName(context)) to stringResource(R.string.hidden_enter_again)
        Step.Biometrics -> stringResource(R.string.hidden_biometrics_title) to
            stringResource(sentence(store.type, R.string.hidden_biometrics_subtitle_pin, R.string.hidden_biometrics_subtitle_pattern, R.string.hidden_biometrics_subtitle_password, R.string.hidden_biometrics_subtitle_lock))
    }
}

@Composable
private fun ChooseType(onChoose: (LockType) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(
            LockType.Pin to stringResource(R.string.hidden_pin_hint),
            LockType.Pattern to stringResource(R.string.hidden_pattern_hint),
            LockType.Password to stringResource(R.string.hidden_password_hint),
        ).forEach { (type, hint) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .pressable({ onChoose(type) }, pressedScale = 0.97f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.onBackground.copy(alpha = 0.07f))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Text(type.localName(context), color = colors.onBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(hint, color = colors.onBackground.copy(alpha = 0.6f), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun BiometricsOffer(onEnable: () -> Unit, onSkip: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .pressable(onEnable, pressedScale = 0.97f)
                .clip(RoundedCornerShape(14.dp))
                .background(Accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.hidden_use_biometrics), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            stringResource(R.string.common_not_now),
            color = Accent,
            fontSize = 17.sp,
            modifier = Modifier
                .clickable(onClick = onSkip)
                .padding(16.dp),
        )
    }
}

/** iOS-style keypad. With [autoLength] > 0 (unlocking) it checks as soon as that many digits are in. */
@Composable
private fun PinEntry(autoLength: Int, onSubmit: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var pin by remember { mutableStateOf("") }
    val dots = if (autoLength > 0) autoLength else maxOf(4, pin.length)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(dots) { i ->
                Box(
                    Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(if (i < pin.length) colors.onBackground else colors.onBackground.copy(alpha = 0.18f)),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .size(76.dp)
                            .then(
                                if (key.isEmpty()) Modifier
                                else Modifier
                                    .pressable({
                                        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        if (key == "⌫") {
                                            pin = pin.dropLast(1)
                                        } else if (pin.length < 8) {
                                            pin += key
                                            if (autoLength > 0 && pin.length == autoLength) {
                                                onSubmit(pin)
                                            }
                                        }
                                    }, pressedScale = 0.9f)
                                    .clip(CircleShape)
                                    .background(if (key == "⌫") Color.Transparent else colors.onBackground.copy(alpha = 0.08f)),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == "⌫") {
                            Icon(LumenIcons.Back, stringResource(R.string.common_delete), tint = colors.onBackground, modifier = Modifier.size(22.dp))
                        } else {
                            Text(key, color = colors.onBackground, fontSize = 30.sp)
                        }
                    }
                }
            }
        }
        if (autoLength == 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.common_next),
                color = if (pin.length in 4..8) Accent else colors.onBackground.copy(alpha = 0.3f),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(enabled = pin.length in 4..8) { onSubmit(pin) }
                    .padding(12.dp),
            )
        }
    }
}

/** 3 × 3 pattern; dots join as the finger passes over them, and lifting submits the sequence. */
@Composable
private fun PatternEntry(onSubmit: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val nodes = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        Modifier
            .size(300.dp)
            .pointerInput(Unit) {
                val cell = size.width / 3f
                fun center(i: Int) = Offset(cell * (i % 3) + cell / 2, cell * (i / 3) + cell / 2)
                fun hit(p: Offset) {
                    val i = (0 until 9).firstOrNull { (center(it) - p).getDistance() < cell * 0.32f } ?: return
                    if (i !in nodes) {
                        nodes += i
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                }
                detectDragGestures(
                    onDragStart = { p ->
                        nodes.clear()
                        finger = p
                        hit(p)
                    },
                    onDrag = { change, _ ->
                        finger = change.position
                        hit(change.position)
                    },
                    onDragEnd = {
                        finger = null
                        val pattern = nodes.joinToString("")
                        nodes.clear()
                        if (pattern.isNotEmpty()) onSubmit(pattern)
                    },
                    onDragCancel = {
                        finger = null
                        nodes.clear()
                    },
                )
            },
    ) {
        val cell = size.width / 3f
        fun center(i: Int) = Offset(cell * (i % 3) + cell / 2, cell * (i / 3) + cell / 2)
        val stroke = 6.dp.toPx()
        nodes.zipWithNext().forEach { (a, b) -> drawLine(Accent, center(a), center(b), stroke, StrokeCap.Round) }
        val last = nodes.lastOrNull()
        val f = finger
        if (last != null && f != null) drawLine(Accent.copy(alpha = 0.5f), center(last), f, stroke, StrokeCap.Round)
        for (i in 0 until 9) {
            val selected = i in nodes
            if (selected) drawCircle(Accent.copy(alpha = 0.2f), radius = 26.dp.toPx(), center = center(i))
            drawCircle(
                if (selected) Accent else colors.onBackground.copy(alpha = 0.35f),
                radius = 8.dp.toPx(),
                center = center(i),
            )
        }
    }
}

@Composable
private fun PasswordEntry(action: String, onSubmit: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = TextStyle(color = colors.onBackground, fontSize = 18.sp),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotEmpty()) onSubmit(text) }),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.onBackground.copy(alpha = 0.08f))
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .focusRequester(focus),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            action,
            color = if (text.isNotEmpty()) Accent else colors.onBackground.copy(alpha = 0.3f),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(enabled = text.isNotEmpty()) { onSubmit(text) }
                .padding(12.dp)
                .width(120.dp),
            textAlign = TextAlign.Center,
        )
    }
}
