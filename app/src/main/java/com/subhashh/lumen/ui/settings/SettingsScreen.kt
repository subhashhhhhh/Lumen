package com.subhashh.lumen.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import com.subhashh.lumen.ui.library.SectionMode
import com.subhashh.lumen.ui.theme.LocalMotion
import com.subhashh.lumen.data.HdrMode
import androidx.compose.runtime.mutableStateOf
import com.subhashh.lumen.data.LumenPrefs
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.subhashh.lumen.ui.theme.LocalDarkTheme
import com.subhashh.lumen.ui.theme.Appearance
import com.subhashh.lumen.ui.theme.LocalAppearance
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.subhashh.lumen.ui.icons.LumenIcons

/** Settings' pages. The main page lists these; each opens its own page. */
enum class SettingsCategory(val key: String, val title: String) {
    Appearance("appearance", "Appearance"),
    Library("library", "Library"),
    PhotosVideos("photos", "Photos & Videos"),
    Privacy("privacy", "Privacy & Security"),
    SmartSearch("smart-search", "Smart Search"),
    Permissions("permissions", "Permissions"),
    Backup("backup", "Backup & Import"),
    About("about", "About");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/** One searchable setting: where it lives and the words people might look for it by. */
private data class SettingEntry(
    val id: String,
    val title: String,
    val category: SettingsCategory,
    val keywords: String = "",
)

private val Entries = listOf(
    SettingEntry("theme", "Theme", SettingsCategory.Appearance, "dark mode light mode night appearance"),
    SettingEntry("reduce-motion", "Reduce Motion", SettingsCategory.Appearance, "animations accessibility springs"),
    SettingEntry("date-headers", "Date Headers", SettingsCategory.Library, "sections days months years grouping"),
    SettingEntry("excluded", "Excluded Folders", SettingsCategory.Library, "ignore folders hide whatsapp junk"),
    SettingEntry("curved-edge", "Curved Edge Display", SettingsCategory.Library, "fast scroll scrollbar handle edge"),
    SettingEntry("ultra-hdr", "Ultra HDR", SettingsCategory.PhotosVideos, "hdr brightness glow"),
    SettingEntry("autoplay", "Autoplay Videos", SettingsCategory.PhotosVideos, "video play automatically"),
    SettingEntry("loop", "Loop Videos", SettingsCategory.PhotosVideos, "repeat video"),
    SettingEntry("slide-time", "Slideshow: Each Photo For", SettingsCategory.PhotosVideos, "slideshow seconds duration timing"),
    SettingEntry("slide-transition", "Slideshow: Transition", SettingsCategory.PhotosVideos, "slideshow fade slide"),
    SettingEntry("slide-shuffle", "Slideshow: Shuffle", SettingsCategory.PhotosVideos, "slideshow random order"),
    SettingEntry("slide-repeat", "Slideshow: Repeat", SettingsCategory.PhotosVideos, "slideshow loop"),
    SettingEntry("lumen-lock", "Lumen Lock", SettingsCategory.Privacy, "pin pattern password change set up passcode"),
    SettingEntry("biometrics", "Fingerprint or Face", SettingsCategory.Privacy, "biometric unlock fingerprint face"),
    SettingEntry("app-lock", "App Lock", SettingsCategory.Privacy, "lock app open protect"),
    SettingEntry("lock-after", "Lock After", SettingsCategory.Privacy, "timeout minutes app lock"),
    SettingEntry("hidden", "Hidden", SettingsCategory.Privacy, "hide private secret vault"),
    SettingEntry("encrypt", "Encrypt Hidden", SettingsCategory.Privacy, "encryption vault secure"),
    SettingEntry("search-quality", "Search Quality", SettingsCategory.SmartSearch, "accurate fast ai model smart search"),
    SettingEntry("search-model", "Search Model", SettingsCategory.SmartSearch, "download remove update ai smart search"),
    SettingEntry("indexed", "Indexed", SettingsCategory.SmartSearch, "indexing progress smart search"),
    SettingEntry("wifi-only", "Download on Wi-Fi Only", SettingsCategory.SmartSearch, "mobile data cellular download"),
    SettingEntry("hf-token", "Hugging Face Token", SettingsCategory.SmartSearch, "huggingface token download speed"),
    SettingEntry("media-management", "Media Management", SettingsCategory.Permissions, "delete favorite trash permission confirm"),
    SettingEntry("all-files", "All Files Access", SettingsCategory.Permissions, "storage hidden folders permission rename"),
    SettingEntry("location", "Location in Photos", SettingsCategory.Permissions, "gps map places permission"),
    SettingEntry("export", "Export Settings", SettingsCategory.Backup, "backup save"),
    SettingEntry("import", "Import Settings", SettingsCategory.Backup, "restore backup"),
    SettingEntry("import-favorites", "Import Favorites", SettingsCategory.Backup, "fossify aves favourites hearts"),
    SettingEntry("licenses", "Open-Source Licenses", SettingsCategory.About, "credits open source"),
    SettingEntry("version", "Version", SettingsCategory.About, "about app version"),
)

/** Every query word must appear in the setting's name, page or keywords. */
private fun search(query: String): List<SettingEntry> {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return Entries.filter { e ->
        val text = "${e.title} ${e.category.title} ${e.keywords}".lowercase()
        words.all { it in text }
    }
}

/**
 * Settings: the main page lists [SettingsCategory]s (with what each is set to) under a search
 * field; [category] opens one. [highlight] is a setting id to scroll to and flash, arriving
 * from a search result.
 */
@Composable
fun SettingsScreen(
    category: SettingsCategory?,
    highlight: String?,
    onOpenCategory: (SettingsCategory, highlight: String?) -> Unit,
    contentPadding: PaddingValues,
    onOpenLicenses: () -> Unit,
    onOpenExcluded: () -> Unit,
    onOpenHidden: () -> Unit,
    hiddenLockSet: Boolean,
    onChangeLock: () -> Unit,
    biometricsOn: Boolean,
    onToggleBiometrics: () -> Unit,
    excludedCount: Int,
    appLockOn: Boolean,
    onToggleAppLock: (Boolean) -> Unit,
    encryptHidden: Boolean,
    onToggleEncryptHidden: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onImportFavorites: () -> Unit,
    onConfirm: (com.subhashh.lumen.ui.components.Confirmation) -> Unit,
    onEnterHfToken: () -> Unit,
    /** Bumped when the Hugging Face token is saved, so the row re-reads it. */
    hfTokenVersion: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Permissions change in system settings; re-read them whenever we come back.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val status = remember(refresh) { PermissionStatus.read(context) }
    val prefs = remember { LumenPrefs(context) }

    SettingsPage(contentPadding, modifier) {
        when (category) {
            null -> {
                LargeTitle("Settings")
                var query by rememberSaveable { mutableStateOf("") }
                SearchBox(query, onQueryChange = { query = it })
                if (query.isNotBlank()) {
                    val results = search(query)
                    if (results.isEmpty()) {
                        Text(
                            "No settings match “${query.trim()}”.",
                            color = groupColors().secondary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    } else {
                        Group("Results") {
                            results.forEachIndexed { i, entry ->
                                if (i > 0) Divider()
                                SettingsRow(
                                    title = entry.title,
                                    subtitle = entry.category.title,
                                    onClick = { onOpenCategory(entry.category, entry.id) },
                                    icon = entry.category.icon(),
                                    iconColor = entry.category.color(),
                                )
                            }
                        }
                    }
                } else {
                    val appearance = LocalAppearance.current
                    val smartSearchOn = remember(refresh) { com.subhashh.lumen.data.ai.ModelDownloads.installed(context) != null }
                    val granted = listOf(status.manageMedia || Build.VERSION.SDK_INT < Build.VERSION_CODES.S, status.allFiles, status.mediaLocation)
                    val open = { c: SettingsCategory -> onOpenCategory(c, null) }
                    Group("") {
                        CategoryRow(SettingsCategory.Appearance, appearance.current.label, open)
                        Divider()
                        CategoryRow(SettingsCategory.Library, null, open)
                        Divider()
                        CategoryRow(SettingsCategory.PhotosVideos, null, open)
                    }
                    Group("") {
                        CategoryRow(SettingsCategory.Privacy, if (appLockOn) "App Lock On" else null, open)
                        Divider()
                        CategoryRow(SettingsCategory.SmartSearch, if (smartSearchOn) "On" else "Off", open)
                    }
                    Group("") {
                        CategoryRow(SettingsCategory.Permissions, "${granted.count { it }} of ${granted.size}", open)
                        Divider()
                        CategoryRow(SettingsCategory.Backup, null, open)
                        Divider()
                        CategoryRow(SettingsCategory.About, context.versionName(), open)
                    }
                }
            }
            else -> {
                LargeTitle(category.title)
                when (category) {
                    SettingsCategory.Appearance -> AppearancePage(highlight)
                    SettingsCategory.Library -> LibraryPage(prefs, highlight, excludedCount, onOpenExcluded)
                    SettingsCategory.PhotosVideos -> PhotosPage(prefs, highlight)
                    SettingsCategory.Privacy -> PrivacyPage(
                        prefs, highlight, appLockOn, onToggleAppLock, onOpenHidden, encryptHidden, onToggleEncryptHidden,
                        hiddenLockSet, onChangeLock, biometricsOn, onToggleBiometrics,
                    )
                    SettingsCategory.SmartSearch ->
                        SmartSearchGroup(prefs, refresh + hfTokenVersion, onConfirm, onEnterHfToken, highlight)
                    SettingsCategory.Permissions -> PermissionsPage(status, highlight)
                    SettingsCategory.Backup -> BackupPage(highlight, onExport, onImport, onImportFavorites)
                    SettingsCategory.About -> AboutPage(highlight, onOpenLicenses)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CategoryRow(category: SettingsCategory, value: String?, onOpen: (SettingsCategory) -> Unit) {
    SettingsRow(title = category.title, value = value, onClick = { onOpen(category) }, icon = category.icon(), iconColor = category.color())
}

private fun SettingsCategory.icon() = when (this) {
    SettingsCategory.Appearance -> LumenIcons.DarkTheme
    SettingsCategory.Library -> LumenIcons.Library
    SettingsCategory.PhotosVideos -> LumenIcons.Image
    SettingsCategory.Privacy -> LumenIcons.Lock
    SettingsCategory.SmartSearch -> LumenIcons.Search
    SettingsCategory.Permissions -> LumenIcons.Shield
    SettingsCategory.Backup -> LumenIcons.Export
    SettingsCategory.About -> LumenIcons.Info
}

private fun SettingsCategory.color() = when (this) {
    SettingsCategory.Appearance -> Indigo
    SettingsCategory.Library -> Blue
    SettingsCategory.PhotosVideos -> Orange
    SettingsCategory.Privacy -> Blue
    SettingsCategory.SmartSearch -> Purple
    SettingsCategory.Permissions -> Green
    SettingsCategory.Backup -> Teal
    SettingsCategory.About -> Gray
}

/** The search field on Settings' main page. */
@Composable
private fun SearchBox(query: String, onQueryChange: (String) -> Unit) {
    val colors = groupColors()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(LumenIcons.Search, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search settings", color = colors.secondary, fontSize = 17.sp)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.text, fontSize = 17.sp),
                cursorBrush = SolidColor(Blue),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                LumenIcons.Close,
                contentDescription = "Clear",
                tint = colors.secondary,
                modifier = Modifier.size(18.dp).clickable { onQueryChange("") },
            )
        }
    }
}

/**
 * Wraps one setting so a search result can bring it into view and flash it. [id] is the
 * setting's entry id; [highlight] the one that was searched for, if any.
 */
@Composable
internal fun Anchor(id: String, highlight: String?, content: @Composable () -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(highlight) {
        if (highlight == id) {
            delay(250) // let the page lay out first
            requester.bringIntoView()
            flash.animateTo(1f, tween(180))
            flash.animateTo(0f, tween(900, delayMillis = 500))
        }
    }
    Box(
        Modifier
            .bringIntoViewRequester(requester)
            .drawBehind { drawRect(Blue.copy(alpha = 0.28f * flash.value)) },
    ) { content() }
}

@Composable
private fun ColumnScope.AppearancePage(highlight: String?) {
    val appearance = LocalAppearance.current
    val motion = LocalMotion.current
    Group("Display") {
        Anchor("theme", highlight) {
            PickerRow(
                title = "Theme",
                options = Appearance.entries.map { it.label },
                selected = appearance.current.ordinal,
                onSelect = { appearance.onChange(Appearance.entries[it]) },
                icon = LumenIcons.DarkTheme,
                iconColor = Indigo,
            )
        }
        Divider()
        Anchor("reduce-motion", highlight) {
            ToggleRow(
                title = "Reduce Motion",
                subtitle = "No flying photos, springs or bounces",
                checked = motion.reduce,
                onCheckedChange = motion.onChange,
                icon = LumenIcons.Accessibility,
                iconColor = Blue,
            )
        }
    }
}

@Composable
private fun ColumnScope.LibraryPage(prefs: LumenPrefs, highlight: String?, excludedCount: Int, onOpenExcluded: () -> Unit) {
    var sections by remember { mutableStateOf(prefs.sectionMode) }
    var curvedEdge by remember { mutableStateOf(prefs.curvedEdge) }
    Group("Grid", footer = "Headers show when photos are sorted by date.") {
        Anchor("date-headers", highlight) {
            PickerRow(
                title = "Date Headers",
                options = SectionMode.entries.map { it.label },
                selected = sections.ordinal,
                onSelect = {
                    sections = SectionMode.entries[it]
                    prefs.sectionMode = sections
                },
                icon = LumenIcons.Library,
                iconColor = Blue,
            )
        }
        Divider()
        Anchor("curved-edge", highlight) {
            ToggleRow(
                title = "Curved Edge Display",
                subtitle = "Keep the fast-scroll handle off the curve, where touches are ignored",
                checked = curvedEdge,
                onCheckedChange = {
                    curvedEdge = it
                    prefs.curvedEdge = it
                },
                icon = LumenIcons.Screenshot,
                iconColor = Gray,
            )
        }
    }
    Group("Folders") {
        Anchor("excluded", highlight) {
            SettingsRow(
                title = "Excluded Folders",
                subtitle = "Folders Lumen ignores",
                value = if (excludedCount == 0) "None" else excludedCount.toString(),
                onClick = onOpenExcluded,
                icon = LumenIcons.FolderProhibited,
                iconColor = Gray,
            )
        }
    }
}

@Composable
private fun ColumnScope.PhotosPage(prefs: LumenPrefs, highlight: String?) {
    var hdrMode by remember { mutableStateOf(prefs.hdrMode) }
    var autoplay by remember { mutableStateOf(prefs.videoAutoplay) }
    var loop by remember { mutableStateOf(prefs.videoLoop) }
    val hdrSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    if (hdrSupported) {
        Group(
            "Photos",
            footer = "Ultra HDR photos can glow brighter than white. Match Brightness keeps to your brightness; " +
                "Full HDR turns the screen all the way up while an HDR photo or video is shown, like Chrome.",
        ) {
            Anchor("ultra-hdr", highlight) {
                PickerRow(
                    title = "Ultra HDR",
                    options = HdrMode.entries.map { it.label },
                    selected = hdrMode.ordinal,
                    onSelect = {
                        hdrMode = HdrMode.entries[it]
                        prefs.hdrMode = hdrMode
                    },
                    icon = LumenIcons.Hdr,
                    iconColor = Orange,
                )
            }
        }
    }
    Group("Videos") {
        Anchor("autoplay", highlight) {
            ToggleRow(
                title = "Autoplay Videos",
                checked = autoplay,
                onCheckedChange = {
                    autoplay = it
                    prefs.videoAutoplay = it
                },
                icon = LumenIcons.Play,
                iconColor = Blue,
            )
        }
        Divider()
        Anchor("loop", highlight) {
            ToggleRow(
                title = "Loop Videos",
                subtitle = "Start over at the end",
                checked = loop,
                onCheckedChange = {
                    loop = it
                    prefs.videoLoop = it
                },
                icon = LumenIcons.Repeat,
                iconColor = Teal,
            )
        }
    }
    var slideSeconds by remember { mutableStateOf(prefs.slideshowSeconds) }
    var slideFade by remember { mutableStateOf(prefs.slideshowFade) }
    var slideShuffle by remember { mutableStateOf(prefs.slideshowShuffle) }
    var slideRepeat by remember { mutableStateOf(prefs.slideshowRepeat) }
    Group("Slideshow") {
        Anchor("slide-time", highlight) {
            PickerRow(
                title = "Each Photo For",
                options = SlideSeconds.map { "$it seconds" },
                selected = SlideSeconds.indexOf(slideSeconds).coerceAtLeast(0),
                onSelect = {
                    slideSeconds = SlideSeconds[it]
                    prefs.slideshowSeconds = slideSeconds
                },
                icon = LumenIcons.Timer,
                iconColor = Purple,
            )
        }
        Divider()
        Anchor("slide-transition", highlight) {
            PickerRow(
                title = "Transition",
                options = listOf("Slide", "Fade"),
                selected = if (slideFade) 1 else 0,
                onSelect = {
                    slideFade = it == 1
                    prefs.slideshowFade = slideFade
                },
                icon = LumenIcons.SlideTransition,
                iconColor = Pink,
            )
        }
        Divider()
        Anchor("slide-shuffle", highlight) {
            ToggleRow(
                title = "Shuffle",
                checked = slideShuffle,
                onCheckedChange = {
                    slideShuffle = it
                    prefs.slideshowShuffle = it
                },
                icon = LumenIcons.Shuffle,
                iconColor = Orange,
            )
        }
        Divider()
        Anchor("slide-repeat", highlight) {
            ToggleRow(
                title = "Repeat",
                subtitle = "Start over at the end",
                checked = slideRepeat,
                onCheckedChange = {
                    slideRepeat = it
                    prefs.slideshowRepeat = it
                },
                icon = LumenIcons.Repeat,
                iconColor = Teal,
            )
        }
    }
}

@Composable
private fun ColumnScope.PrivacyPage(
    prefs: LumenPrefs,
    highlight: String?,
    appLockOn: Boolean,
    onToggleAppLock: (Boolean) -> Unit,
    onOpenHidden: () -> Unit,
    encryptHidden: Boolean,
    onToggleEncryptHidden: (Boolean) -> Unit,
    hiddenLockSet: Boolean,
    onChangeLock: () -> Unit,
    biometricsOn: Boolean,
    onToggleBiometrics: () -> Unit,
) {
    Group(
        "Lumen Lock",
        footer = "Lumen's lock (PIN, pattern or password) guards Hidden, and with App Lock all of Lumen. " +
            "It's separate from your phone's screen lock.",
    ) {
        Anchor("lumen-lock", highlight) {
            SettingsRow(
                title = if (hiddenLockSet) "Change Lumen Lock" else "Set Up Lumen Lock",
                onClick = onChangeLock,
                icon = LumenIcons.Lock,
                iconColor = Blue,
            )
        }
        if (hiddenLockSet) {
            Divider()
            Anchor("biometrics", highlight) {
                ToggleRow(
                    title = "Fingerprint or Face",
                    subtitle = "Unlock faster",
                    checked = biometricsOn,
                    onCheckedChange = { onToggleBiometrics() },
                    icon = LumenIcons.Fingerprint,
                    iconColor = Red,
                )
            }
        }
        Divider()
        Anchor("app-lock", highlight) {
            ToggleRow(
                title = "App Lock",
                subtitle = "Ask for Lumen's lock when opening Lumen",
                checked = appLockOn,
                onCheckedChange = onToggleAppLock,
                icon = LumenIcons.Lock,
                iconColor = Indigo,
            )
        }
        if (appLockOn) {
            Divider()
            var minutes by remember { mutableStateOf(prefs.appLockMinutes) }
            Anchor("lock-after", highlight) {
                PickerRow(
                    title = "Lock After",
                    options = LockMinutes.map { if (it == 0) "Immediately" else if (it == 1) "1 minute" else "$it minutes" },
                    selected = LockMinutes.indexOf(minutes).coerceAtLeast(0),
                    onSelect = {
                        minutes = LockMinutes[it]
                        prefs.appLockMinutes = minutes
                    },
                    icon = LumenIcons.Timer,
                    iconColor = Purple,
                )
            }
        }
    }
    Group("Hidden") {
        Anchor("hidden", highlight) {
            SettingsRow(
                title = "Hidden",
                subtitle = "Out of your library and most apps, locked in Lumen",
                onClick = onOpenHidden,
                icon = LumenIcons.Hide,
                iconColor = Indigo,
            )
        }
        Divider()
        Anchor("encrypt", highlight) {
            ToggleRow(
                title = "Encrypt Hidden",
                subtitle = "So no other app or file manager can see them",
                checked = encryptHidden,
                onCheckedChange = onToggleEncryptHidden,
                icon = LumenIcons.Shield,
                iconColor = Green,
            )
        }
    }
}

@Composable
private fun ColumnScope.PermissionsPage(status: PermissionStatus, highlight: String?) {
    val context = LocalContext.current
    Group("Android Permissions", footer = "Lumen works without these; they unlock the features named. Each opens Android's settings.") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Anchor("media-management", highlight) {
                SettingsRow(
                    title = "Media Management",
                    subtitle = "Delete and favorite without asking each time",
                    value = if (status.manageMedia) "On" else "Off",
                    onClick = { context.openSettings(Settings.ACTION_REQUEST_MANAGE_MEDIA) },
                    icon = LumenIcons.Shield,
                    iconColor = Green,
                )
            }
            Divider()
        }
        Anchor("all-files", highlight) {
            SettingsRow(
                title = "All Files Access",
                subtitle = "Hidden folders, renaming albums, dates on videos",
                value = if (status.allFiles) "On" else "Off",
                onClick = { context.openSettings(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION) },
                icon = LumenIcons.FolderOpen,
                iconColor = Blue,
            )
        }
        Divider()
        Anchor("location", highlight) {
            SettingsRow(
                title = "Location in Photos",
                subtitle = "Show where photos were taken",
                value = if (status.mediaLocation) "On" else "Off",
                onClick = { context.openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) },
                icon = LumenIcons.Location,
                iconColor = Green,
            )
        }
    }
}

@Composable
private fun ColumnScope.BackupPage(highlight: String?, onExport: () -> Unit, onImport: () -> Unit, onImportFavorites: () -> Unit) {
    Group("Lumen Backup", footer = "The Hidden lock is never included, so a backup can't unlock Hidden.") {
        Anchor("export", highlight) {
            SettingsRow(
                title = "Export Settings…",
                subtitle = "Settings, album arrangement and favorites",
                onClick = onExport,
                icon = LumenIcons.Export,
                iconColor = Green,
            )
        }
        Divider()
        Anchor("import", highlight) {
            SettingsRow(
                title = "Import Settings…",
                subtitle = "From a Lumen backup file",
                onClick = onImport,
                icon = LumenIcons.Import,
                iconColor = Blue,
            )
        }
    }
    Group("From Other Apps") {
        Anchor("import-favorites", highlight) {
            SettingsRow(
                title = "Import Favorites…",
                subtitle = "From Fossify Gallery (.txt) or Aves (.json)",
                onClick = onImportFavorites,
                icon = LumenIcons.HeartFilled,
                iconColor = Pink,
            )
        }
    }
}

@Composable
private fun ColumnScope.AboutPage(highlight: String?, onOpenLicenses: () -> Unit) {
    val context = LocalContext.current
    Group("Lumen") {
        Anchor("licenses", highlight) {
            SettingsRow(
                title = "Open-Source Licenses",
                onClick = onOpenLicenses,
                icon = LumenIcons.Document,
                iconColor = Gray,
            )
        }
        Divider()
        Anchor("version", highlight) {
            SettingsRow(
                title = "Version",
                value = context.versionName(),
                onClick = null,
                icon = LumenIcons.Info,
                iconColor = Gray,
            )
        }
    }
}

/** App Lock's "Lock After" choices, in minutes in the background. */
private val LockMinutes = listOf(0, 1, 5, 15)

/** Slideshow intervals the row cycles through, in seconds. */
private val SlideSeconds = listOf(2, 3, 5, 10)

private data class PermissionStatus(val manageMedia: Boolean, val allFiles: Boolean, val mediaLocation: Boolean) {
    companion object {
        fun read(context: Context) = PermissionStatus(
            manageMedia = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context),
            allFiles = Environment.isExternalStorageManager(),
            mediaLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
}

/** Opens a per-app system settings page for Lumen. */
private fun Context.openSettings(action: String) {
    val intent = Intent(action, "package:$packageName".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
        .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

private fun Context.versionName(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()

internal val Indigo = Color(0xFF5E5CE6)
internal val Blue = Color(0xFF0A84FF)
internal val Teal = Color(0xFF30B0C7)
internal val Purple = Color(0xFFBF5AF2)
private val Pink = Color(0xFFFF375F)
private val Orange = Color(0xFFFF9F0A)
internal val Green = Color(0xFF30D158)
private val Red = Color(0xFFFF453A)
