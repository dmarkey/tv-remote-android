package com.dmarkey.tvremote.ui

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmarkey.tvremote.FoundTv
import com.dmarkey.tvremote.Haptics
import com.dmarkey.tvremote.Link
import com.dmarkey.tvremote.PairState
import com.dmarkey.tvremote.RemoteViewModel
import com.dmarkey.tvremote.Screen
import com.dmarkey.tvremote.Tv
import com.dmarkey.tvremote.TvSession
import com.dmarkey.tvremote.discoverTvs

/** True black for the AMOLED panel; one violet accent. */
object Palette {
    val background = Color(0xFF000000)
    val key = Color(0xFF161618)
    val keyPressed = Color(0xFF34343A)
    val label = Color(0xFFE8E8EC)
    val dim = Color(0xFF8A8A92)
    val accent = Color(0xFF7C4DFF)
    val accentPressed = Color(0xFF9F7BFF)
    val power = Color(0xFF3A1416)
    val good = Color(0xFF3DDC84)
    val warn = Color(0xFFFFB300)
    val bad = Color(0xFFFF5252)
}

private val colors = darkColorScheme(
    primary = Palette.accent,
    background = Palette.background,
    surface = Color(0xFF121214),
    onSurface = Palette.label,
    onBackground = Palette.label,
)

/**
 * App links the TV resolves to an app. Apps without a web link are launched by
 * package name with market://launch, which opens the installed app directly.
 */
private val apps = listOf(
    "YouTube" to "https://www.youtube.com",
    "Netflix" to "https://www.netflix.com/title",
    "TiviMate" to "market://launch?id=ar.tvplayer.tv",
    "Jellyfin" to "market://launch?id=org.jellyfin.androidtv",
)

@Composable
fun App(vm: RemoteViewModel, haptics: Haptics) {
    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize().background(Palette.background)) {
            when (val screen = vm.screen.collectAsStateWithLifecycle().value) {
                Screen.Remote -> RemoteScreen(vm, haptics)
                Screen.Setup -> SetupScreen(vm)
                is Screen.Pair -> PairScreen(vm, screen.tv)
            }
        }
    }
}

// ---------------------------------------------------------------- remote

@Composable
private fun RemoteScreen(vm: RemoteViewModel, haptics: Haptics) {
    val link by vm.session.link.collectAsStateWithLifecycle()
    val volume by vm.session.volume.collectAsStateWithLifecycle()
    val tv by vm.current.collectAsStateWithLifecycle()
    val tvs by vm.tvs.collectAsStateWithLifecycle()
    var typing by remember { mutableStateOf(false) }
    val s = vm.session

    fun key(code: Int) { s.key(code); haptics.tick() }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top: which TV + rarely-needed actions. The thumb zone below holds everything frequent.
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TvPicker(tv, tvs, link, vm, Modifier.weight(1f))
            IconButton(onClick = { typing = !typing }) {
                Icon(Icons.Rounded.Keyboard, "Keyboard", tint = if (typing) Palette.accent else Palette.label)
            }
            Spacer(Modifier.width(8.dp))
            PadButton(
                onPress = { s.key(KeyEvent.KEYCODE_POWER); haptics.heavy() },
                modifier = Modifier.size(52.dp),
                tint = Palette.power,
            ) { Icon(Icons.Rounded.PowerSettingsNew, "Power", tint = Palette.bad) }
        }

        LinkBanner(link, onRepair = { tv?.let(vm::startPairing) })

        AnimatedVisibility(typing) { TextEntry(onSend = { s.text(it); haptics.click() }, onDelete = { key(KeyEvent.KEYCODE_DEL) }) }

        val live = Modifier.alpha(if (link == Link.Connected) 1f else 0.45f)

        // Everything sits at the bottom (thumb reach). The D-pad takes whatever height is
        // left, so a smaller phone or the open keyboard shrinks it rather than squashing it.
        Column(
            live.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                apps.forEach { (name, url) ->
                    PadButton(
                        onPress = { s.launch(url); haptics.click() },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                    ) { Text(name, color = Palette.label, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundKey(Icons.Rounded.FastRewind, "Rewind", repeat = true) { key(KeyEvent.KEYCODE_MEDIA_REWIND) }
                PadButton(onPress = { key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, modifier = Modifier.size(64.dp)) {
                    Row {
                        Icon(Icons.Rounded.PlayArrow, "Play/pause", Modifier.size(24.dp), tint = Palette.label)
                        Icon(Icons.Rounded.Pause, null, Modifier.size(24.dp), tint = Palette.label)
                    }
                }
                RoundKey(Icons.Rounded.FastForward, "Fast forward", repeat = true) { key(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) }
            }

            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                RoundKey(Icons.AutoMirrored.Rounded.VolumeDown, "Volume down", repeat = true) { key(KeyEvent.KEYCODE_VOLUME_DOWN) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RoundKey(
                        Icons.AutoMirrored.Rounded.VolumeOff, "Mute",
                        tint = if (volume?.muted == true) Palette.warn else Palette.label,
                    ) { key(KeyEvent.KEYCODE_VOLUME_MUTE) }
                    Text(
                        volume?.let { if (it.muted) "muted" else "${it.level}" } ?: " ",
                        color = Palette.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                RoundKey(Icons.AutoMirrored.Rounded.VolumeUp, "Volume up", repeat = true) { key(KeyEvent.KEYCODE_VOLUME_UP) }
            }

            Spacer(Modifier.height(18.dp))

            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false), contentAlignment = Alignment.Center) {
                val padSize = minOf(maxWidth, maxHeight, 360.dp)
                if (padSize >= 140.dp) DPad(
                    size = padSize,
                    onArrow = { zone ->
                        key(
                            when (zone) {
                                Zone.UP -> KeyEvent.KEYCODE_DPAD_UP
                                Zone.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
                                Zone.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                                else -> KeyEvent.KEYCODE_DPAD_RIGHT
                            },
                        )
                    },
                    onOk = { s.key(KeyEvent.KEYCODE_DPAD_CENTER); haptics.click() },
                    onOkLongStart = { s.key(KeyEvent.KEYCODE_DPAD_CENTER, TvSession.START_LONG); haptics.heavy() },
                    onOkLongEnd = { s.key(KeyEvent.KEYCODE_DPAD_CENTER, TvSession.END_LONG) },
                )
            }

            Spacer(Modifier.height(20.dp))

            // Back and Home sit lowest: they're what the thumb reaches for most after the D-pad.
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WideKey(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.weight(1f)) { key(KeyEvent.KEYCODE_BACK) }
                WideKey(Icons.Rounded.Home, "Home", Modifier.weight(1f)) { key(KeyEvent.KEYCODE_HOME) }
                WideKey(Icons.Rounded.Settings, "Settings", Modifier.weight(1f)) { key(KeyEvent.KEYCODE_SETTINGS) }
            }
        }
    }
}

@Composable
private fun RoundKey(
    icon: ImageVector,
    label: String,
    repeat: Boolean = false,
    tint: Color = Palette.label,
    onPress: () -> Unit,
) {
    PadButton(onPress = onPress, modifier = Modifier.size(64.dp), repeat = repeat) {
        Icon(icon, label, Modifier.size(28.dp), tint = tint)
    }
}

@Composable
private fun WideKey(icon: ImageVector, label: String, modifier: Modifier, onPress: () -> Unit) {
    PadButton(onPress = onPress, modifier = modifier.height(60.dp), shape = RoundedCornerShape(30.dp)) {
        Icon(icon, label, Modifier.size(28.dp), tint = Palette.label)
    }
}

@Composable
private fun TvPicker(tv: Tv?, tvs: List<Tv>, link: Link, vm: RemoteViewModel, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).clickable { open = true }.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dot = when (link) {
                Link.Connected -> Palette.good
                Link.Connecting, Link.Idle -> Palette.warn
                else -> Palette.bad
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(10.dp))
            Text(
                tv?.name ?: "No TV", color = Palette.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Rounded.ArrowDropDown, null, tint = Palette.dim)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            tvs.forEach { t ->
                DropdownMenuItem(
                    text = { Text(t.name) },
                    leadingIcon = { Icon(if (t.host == tv?.host) Icons.Rounded.Check else Icons.Rounded.Tv, null) },
                    onClick = { open = false; vm.select(t) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Add a TV") },
                leadingIcon = { Icon(Icons.Rounded.Add, null) },
                onClick = { open = false; vm.addTv() },
            )
            if (tv != null) {
                DropdownMenuItem(
                    text = { Text("Forget ${tv.name}") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = { open = false; vm.forget(tv) },
                )
            }
        }
    }
}

@Composable
private fun LinkBanner(link: Link, onRepair: () -> Unit) {
    when (link) {
        Link.NeedsPairing -> Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A1C00))
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("The TV no longer recognises this phone.", color = Palette.label, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onRepair) { Text("Pair again") }
        }
        is Link.Offline -> Text(
            "Reconnecting…", color = Palette.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
        )
        else -> {}
    }
}

@Composable
private fun TextEntry(onSend: (String) -> Unit, onDelete: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f).focusRequester(focus),
            placeholder = { Text("Type to the TV") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend(text); text = "" }),
        )
        IconButton(onClick = onDelete) { Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete on TV", tint = Palette.label) }
        IconButton(onClick = { onSend(text); text = "" }) { Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = Palette.accent) }
    }
}

// ---------------------------------------------------------------- setup

@Composable
private fun SetupScreen(vm: RemoteViewModel) {
    val context = LocalContext.current
    val found by remember { discoverTvs(context.applicationContext) }.collectAsStateWithLifecycle(emptyList())
    val paired by vm.tvs.collectAsStateWithLifecycle()
    var ip by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Add a TV", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.label, modifier = Modifier.weight(1f))
            if (paired.isNotEmpty()) IconButton(onClick = vm::cancelPairing) { Icon(Icons.Rounded.Close, "Close", tint = Palette.label) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Looking for Android / Google TVs on this Wi-Fi…", color = Palette.dim)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent, trackColor = Palette.key)
        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(found, key = { it.host }) { tv: FoundTv ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.key)
                        .clickable { vm.startPairing(Tv(tv.name, tv.host)) }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Tv, null, tint = Palette.accent)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tv.name, color = Palette.label, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Text(tv.host, color = Palette.dim, fontSize = 13.sp)
                    }
                    if (paired.any { it.host == tv.host }) Text("paired", color = Palette.good, fontSize = 13.sp)
                }
            }
        }

        Text("Or enter its IP address", color = Palette.dim, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it.filter { c -> c.isDigit() || c == '.' } },
                modifier = Modifier.weight(1f),
                placeholder = { Text("192.168.1.50") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (ip.isNotBlank()) vm.startPairing(Tv(ip, ip)) }),
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = { vm.startPairing(Tv(ip, ip)) }, enabled = ip.count { it == '.' } == 3) { Text("Pair") }
        }
    }
}

// ---------------------------------------------------------------- pairing

@Composable
private fun PairScreen(vm: RemoteViewModel, tv: Tv) {
    val state by vm.pair.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Pair ${tv.name}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.label, modifier = Modifier.weight(1f))
            IconButton(onClick = vm::cancelPairing) { Icon(Icons.Rounded.Close, "Cancel", tint = Palette.label) }
        }
        Spacer(Modifier.weight(0.4f))
        when (val st = state) {
            PairState.Connecting -> Busy("Connecting to the TV…")
            PairState.Checking -> Busy("Checking the code…")
            is PairState.EnterCode -> CodeEntry(st.error) { vm.submitCode(tv, it) }
            is PairState.Failed -> {
                Text(st.message, color = Palette.bad, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { vm.startPairing(tv) }) { Text("Try again") }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Busy(message: String) {
    CircularProgressIndicator(color = Palette.accent)
    Spacer(Modifier.height(16.dp))
    Text(message, color = Palette.dim)
}

/** Six hex characters; submits by itself on the sixth. */
@Composable
private fun CodeEntry(error: String?, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(error) { if (error != null) code = "" }

    Text("Enter the code shown on the TV", color = Palette.label, fontSize = 18.sp)
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = code,
        onValueChange = { raw ->
            val clean = raw.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
            code = clean
            if (clean.length == 6) onSubmit(clean)
        },
        modifier = Modifier.width(260.dp).focusRequester(focus),
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 34.sp, letterSpacing = 8.sp, textAlign = TextAlign.Center),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
    )
    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Text(error, color = Palette.bad)
    }
}
