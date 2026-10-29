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
import com.subhashh.lumen.data.FilmStripSize
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.subhashh.lumen.R
import com.subhashh.lumen.ui.icons.LumenIcons

/** Settings' pages. The main page lists these; each opens its own page. */
enum class SettingsCategory(val key: String, @StringRes val titleRes: Int) {
    Appearance("appearance", R.string.settings_category_appearance),
    Library("library", R.string.settings_category_library),
    PhotosVideos("photos", R.string.settings_category_photos_videos),
    Privacy("privacy", R.string.settings_category_privacy),
    SmartSearch("smart-search", R.string.settings_category_smart_features),
    Permissions("permissions", R.string.settings_category_permissions),
    Backup("backup", R.string.settings_category_backup),
    About("about", R.string.settings_category_about),
    Developer("developer", R.string.settings_category_developer);

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/** One searchable setting: where it lives and the words people might look for it by. */
private data class SettingEntry(
    val id: String,
    @StringRes val title: Int,
    val category: SettingsCategory,
    @StringRes val keywords: Int,
)

private val Entries = listOf(
    SettingEntry("theme", R.string.settings_theme, SettingsCategory.Appearance, R.string.settings_kw_theme),
    SettingEntry("reduce-motion", R.string.settings_reduce_motion, SettingsCategory.Appearance, R.string.settings_kw_reduce_motion),
    SettingEntry("liquid-glass", R.string.settings_liquid_glass, SettingsCategory.Appearance, R.string.settings_kw_liquid_glass),
    SettingEntry("reduce-blur", R.string.settings_reduce_blur, SettingsCategory.Appearance, R.string.settings_kw_reduce_blur),
    SettingEntry("date-headers", R.string.settings_date_headers, SettingsCategory.Library, R.string.settings_kw_date_headers),
    SettingEntry("excluded", R.string.settings_excluded_folders, SettingsCategory.Library, R.string.settings_kw_excluded),
    SettingEntry("curved-edge", R.string.settings_curved_edge, SettingsCategory.Library, R.string.settings_kw_curved_edge),
    SettingEntry("ultra-hdr", R.string.settings_ultra_hdr, SettingsCategory.PhotosVideos, R.string.settings_kw_ultra_hdr),
    SettingEntry("film-strip", R.string.settings_film_strip, SettingsCategory.PhotosVideos, R.string.settings_kw_film_strip),
    SettingEntry("maps", R.string.settings_maps, SettingsCategory.PhotosVideos, R.string.settings_kw_maps),
    SettingEntry("autoplay", R.string.settings_autoplay_videos, SettingsCategory.PhotosVideos, R.string.settings_kw_autoplay),
    SettingEntry("loop", R.string.settings_loop_videos, SettingsCategory.PhotosVideos, R.string.settings_kw_loop),
    SettingEntry("slide-time", R.string.settings_entry_slide_time, SettingsCategory.PhotosVideos, R.string.settings_kw_slide_time),
    SettingEntry("slide-transition", R.string.settings_entry_slide_transition, SettingsCategory.PhotosVideos, R.string.settings_kw_slide_transition),
    SettingEntry("slide-shuffle", R.string.settings_entry_slide_shuffle, SettingsCategory.PhotosVideos, R.string.settings_kw_slide_shuffle),
    SettingEntry("slide-repeat", R.string.settings_entry_slide_repeat, SettingsCategory.PhotosVideos, R.string.settings_kw_slide_repeat),
    SettingEntry("lumen-lock", R.string.settings_lumen_lock, SettingsCategory.Privacy, R.string.settings_kw_lumen_lock),
    SettingEntry("biometrics", R.string.settings_biometrics, SettingsCategory.Privacy, R.string.settings_kw_biometrics),
    SettingEntry("app-lock", R.string.settings_app_lock, SettingsCategory.Privacy, R.string.settings_kw_app_lock),
    SettingEntry("lock-after", R.string.settings_lock_after, SettingsCategory.Privacy, R.string.settings_kw_lock_after),
    SettingEntry("hidden", R.string.settings_hidden, SettingsCategory.Privacy, R.string.settings_kw_hidden),
    SettingEntry("encrypt", R.string.settings_encrypt_hidden, SettingsCategory.Privacy, R.string.settings_kw_encrypt),
    SettingEntry("hidden-screenshots", R.string.settings_hidden_screenshots, SettingsCategory.Privacy, R.string.settings_kw_hidden_screenshots),
    SettingEntry("search-quality", R.string.settings_entry_search_quality, SettingsCategory.SmartSearch, R.string.settings_kw_search_quality),
    SettingEntry("search-model", R.string.settings_entry_search_model, SettingsCategory.SmartSearch, R.string.settings_kw_search_model),
    SettingEntry("indexed", R.string.settings_entry_indexed, SettingsCategory.SmartSearch, R.string.settings_kw_indexed),
    SettingEntry("subtitle-quality", R.string.settings_entry_subtitle_quality, SettingsCategory.SmartSearch, R.string.settings_kw_subtitle_quality),
    SettingEntry("subtitles-model", R.string.settings_entry_subtitles_model, SettingsCategory.SmartSearch, R.string.settings_kw_subtitles_model),
    SettingEntry("cutout-model", R.string.settings_entry_cutout_model, SettingsCategory.SmartSearch, R.string.settings_kw_cutout_model),
    SettingEntry("scheduled-indexing", R.string.settings_entry_scheduled_indexing, SettingsCategory.SmartSearch, R.string.settings_kw_scheduled_indexing),
    SettingEntry("index-on-battery", R.string.settings_entry_index_on_battery, SettingsCategory.SmartSearch, R.string.settings_kw_index_on_battery),
    SettingEntry("unrestricted-battery", R.string.settings_entry_unrestricted_battery, SettingsCategory.SmartSearch, R.string.settings_kw_unrestricted_battery),
    SettingEntry("video-detail", R.string.settings_entry_video_detail, SettingsCategory.SmartSearch, R.string.settings_kw_video_detail),
    SettingEntry("video-text-short", R.string.smart_video_text_title, SettingsCategory.SmartSearch, R.string.settings_kw_video_text),
    SettingEntry("face-model", R.string.settings_entry_face_models, SettingsCategory.SmartSearch, R.string.settings_kw_face_model),
    SettingEntry("faces", R.string.settings_entry_faces, SettingsCategory.SmartSearch, R.string.settings_kw_faces),
    SettingEntry("text-model", R.string.settings_entry_text_reader, SettingsCategory.SmartSearch, R.string.settings_kw_text_model),
    SettingEntry("text-read", R.string.settings_entry_text_read, SettingsCategory.SmartSearch, R.string.settings_kw_text_read),
    SettingEntry("wifi-only", R.string.settings_entry_wifi_only, SettingsCategory.SmartSearch, R.string.settings_kw_wifi_only),
    SettingEntry("search-details", R.string.settings_search_details, SettingsCategory.Developer, R.string.settings_kw_search_details),
    SettingEntry("hf-token", R.string.settings_entry_hf_token, SettingsCategory.SmartSearch, R.string.settings_kw_hf_token),
    SettingEntry("media-management", R.string.settings_media_management, SettingsCategory.Permissions, R.string.settings_kw_media_management),
    SettingEntry("all-files", R.string.settings_all_files, SettingsCategory.Permissions, R.string.settings_kw_all_files),
    SettingEntry("location", R.string.settings_location_in_photos, SettingsCategory.Permissions, R.string.settings_kw_location),
    SettingEntry("export", R.string.settings_entry_export, SettingsCategory.Backup, R.string.settings_kw_export),
    SettingEntry("import", R.string.settings_entry_import, SettingsCategory.Backup, R.string.settings_kw_import),
    SettingEntry("import-favorites", R.string.settings_entry_import_favorites, SettingsCategory.Backup, R.string.settings_kw_import_favorites),
    SettingEntry("licenses", R.string.settings_licenses, SettingsCategory.About, R.string.settings_kw_licenses),
    SettingEntry("software-update", R.string.settings_software_update, SettingsCategory.About, R.string.settings_kw_software_update),
    SettingEntry("auto-update", R.string.settings_entry_auto_update, SettingsCategory.About, R.string.settings_kw_auto_update),
    SettingEntry("github", R.string.settings_github, SettingsCategory.About, R.string.settings_kw_github),
    SettingEntry("report-bug", R.string.settings_report_bug, SettingsCategory.About, R.string.settings_kw_report_bug),
    SettingEntry("version", R.string.settings_version, SettingsCategory.About, R.string.settings_kw_version),
)

/** Every query word must appear in the setting's name, page or keywords. */
private fun search(context: Context, query: String, developer: Boolean): List<SettingEntry> {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return Entries.filter { e ->
        if (e.category == SettingsCategory.Developer && !developer) return@filter false
        val text = "${context.getString(e.title)} ${context.getString(e.category.titleRes)} ${context.getString(e.keywords)}".lowercase()
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
                LargeTitle(stringResource(R.string.common_settings))
                var query by rememberSaveable { mutableStateOf("") }
                SearchBox(query, onQueryChange = { query = it })
                if (query.isNotBlank()) {
                    val results = search(context, query, prefs.developerOptions)
                    if (results.isEmpty()) {
                        Text(
                            stringResource(R.string.settings_no_match, query.trim()),
                            color = groupColors().secondary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    } else {
                        Group(stringResource(R.string.settings_results)) {
                            results.forEachIndexed { i, entry ->
                                if (i > 0) Divider()
                                SettingsRow(
                                    title = stringResource(entry.title),
                                    subtitle = stringResource(entry.category.titleRes),
                                    onClick = { onOpenCategory(entry.category, entry.id) },
                                    icon = entry.category.icon(),
                                    iconColor = entry.category.color(),
                                )
                            }
                        }
                    }
                } else {
                    val appearance = LocalAppearance.current
                    val smartSearchOn = remember(refresh) {
                        com.subhashh.lumen.ai.ModelPack.entries.any { com.subhashh.lumen.ai.ModelDownloads.usable(context, it) }
                    }
                    val granted = listOf(status.manageMedia || Build.VERSION.SDK_INT < Build.VERSION_CODES.S, status.allFiles, status.mediaLocation)
                    val open = { c: SettingsCategory -> onOpenCategory(c, null) }
                    // A newer Lumen found by Software Update, on top like iOS's.
                    val update = (UpdateSession.state as? UpdateSession.State.Result)?.check
                        .let { (it as? com.subhashh.lumen.data.Updater.Check.Available)?.release?.version }
                        ?: prefs.availableUpdate
                    if (update != null && com.subhashh.lumen.data.Updater.isNewer(update, context.versionName())) {
                        Group("") {
                            SettingsRow(
                                title = stringResource(R.string.settings_update_available, update),
                                onClick = { onOpenCategory(SettingsCategory.About, "software-update") },
                                icon = LumenIcons.Import,
                                iconColor = Blue,
                            )
                        }
                    }
                    Group("") {
                        CategoryRow(SettingsCategory.Appearance, stringResource(appearance.current.labelRes), open)
                        Divider()
                        CategoryRow(SettingsCategory.Library, null, open)
                        Divider()
                        CategoryRow(SettingsCategory.PhotosVideos, null, open)
                    }
                    Group("") {
                        CategoryRow(SettingsCategory.Privacy, if (appLockOn) stringResource(R.string.settings_app_lock_on) else null, open)
                        Divider()
                        CategoryRow(SettingsCategory.SmartSearch, stringResource(if (smartSearchOn) R.string.common_on else R.string.common_off), open)
                    }
                    Group("") {
                        CategoryRow(SettingsCategory.Permissions, stringResource(R.string.settings_permissions_count, granted.count { it }, granted.size), open)
                        Divider()
                        CategoryRow(SettingsCategory.Backup, null, open)
                        Divider()
                        CategoryRow(SettingsCategory.About, context.versionName(), open)
                    }
                    // Unlocked from About (Version, seven taps), like Android's.
                    val developer = remember(refresh) { prefs.developerOptions }
                    if (developer) {
                        Group("") { CategoryRow(SettingsCategory.Developer, null, open) }
                    }
                }
            }
            else -> {
                LargeTitle(stringResource(category.titleRes))
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
                    SettingsCategory.About -> AboutPage(prefs, highlight, onOpenLicenses, onConfirm)
                    SettingsCategory.Developer -> DeveloperPage(prefs, highlight)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CategoryRow(category: SettingsCategory, value: String?, onOpen: (SettingsCategory) -> Unit) {
    SettingsRow(title = stringResource(category.titleRes), value = value, onClick = { onOpen(category) }, icon = category.icon(), iconColor = category.color())
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
    SettingsCategory.Developer -> LumenIcons.Settings
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
    SettingsCategory.Developer -> Gray
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
            if (query.isEmpty()) Text(stringResource(R.string.settings_search_hint), color = colors.secondary, fontSize = 17.sp)
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
                contentDescription = stringResource(R.string.settings_clear),
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
    Group(stringResource(R.string.settings_group_display)) {
        Anchor("theme", highlight) {
            PickerRow(
                title = stringResource(R.string.settings_theme),
                options = Appearance.entries.map { stringResource(it.labelRes) },
                selected = appearance.current.ordinal,
                onSelect = { appearance.onChange(Appearance.entries[it]) },
                icon = LumenIcons.DarkTheme,
                iconColor = Indigo,
            )
        }
        Divider()
        Anchor("reduce-motion", highlight) {
            ToggleRow(
                title = stringResource(R.string.settings_reduce_motion),
                subtitle = stringResource(R.string.settings_reduce_motion_subtitle),
                checked = motion.reduce,
                onCheckedChange = motion.onChange,
                icon = LumenIcons.Accessibility,
                iconColor = Blue,
            )
        }
    }
    val glass = com.subhashh.lumen.ui.theme.LocalGlass.current
    Group(
        stringResource(R.string.settings_group_glass),
        footer = stringResource(R.string.settings_glass_footer),
    ) {
        Anchor("liquid-glass", highlight) {
            ToggleRow(
                title = stringResource(R.string.settings_liquid_glass),
                checked = glass.liquid,
                onCheckedChange = glass.onLiquid,
                icon = LumenIcons.Image,
                iconColor = Teal,
            )
        }
        Divider()
        Anchor("reduce-blur", highlight) {
            ToggleRow(
                title = stringResource(R.string.settings_reduce_blur),
                subtitle = if (!glass.liquid) stringResource(R.string.settings_reduce_blur_needs_glass) else null,
                checked = glass.reduceBlur && glass.liquid,
                onCheckedChange = { if (glass.liquid) glass.onReduceBlur(it) },
                icon = LumenIcons.Hide,
                iconColor = Purple,
            )
        }
    }
}

@Composable
private fun ColumnScope.LibraryPage(prefs: LumenPrefs, highlight: String?, excludedCount: Int, onOpenExcluded: () -> Unit) {
    var sections by remember { mutableStateOf(prefs.sectionMode) }
    var curvedEdge by remember { mutableStateOf(prefs.curvedEdge) }
    Group(stringResource(R.string.settings_group_grid), footer = stringResource(R.string.settings_grid_footer)) {
        Anchor("date-headers", highlight) {
            PickerRow(
                title = stringResource(R.string.settings_date_headers),
                options = SectionMode.entries.map { stringResource(it.labelRes) },
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
                title = stringResource(R.string.settings_curved_edge),
                subtitle = stringResource(R.string.settings_curved_edge_subtitle),
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
    Group(stringResource(R.string.settings_group_folders)) {
        Anchor("excluded", highlight) {
            SettingsRow(
                title = stringResource(R.string.settings_excluded_folders),
                subtitle = stringResource(R.string.settings_excluded_folders_subtitle),
                value = if (excludedCount == 0) stringResource(R.string.settings_none) else excludedCount.toString(),
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
    var maps by remember { mutableStateOf(prefs.showMaps) }
    val hdrSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    if (hdrSupported) {
        Group(
            stringResource(R.string.settings_group_photos),
            footer = stringResource(R.string.settings_hdr_footer),
        ) {
            Anchor("ultra-hdr", highlight) {
                PickerRow(
                    title = stringResource(R.string.settings_ultra_hdr),
                    options = HdrMode.entries.map { stringResource(it.label) },
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
    var filmStrip by remember { mutableStateOf(prefs.filmStrip) }
    Group(
        stringResource(R.string.settings_group_viewer),
        footer = stringResource(R.string.settings_film_strip_footer),
    ) {
        Anchor("film-strip", highlight) {
            PickerRow(
                title = stringResource(R.string.settings_film_strip),
                options = FilmStripSize.entries.map { stringResource(it.label) },
                selected = filmStrip.ordinal,
                onSelect = {
                    filmStrip = FilmStripSize.entries[it]
                    prefs.filmStrip = filmStrip
                },
                icon = LumenIcons.Image,
                iconColor = Blue,
            )
        }
    }
    Group(
        stringResource(R.string.settings_group_location),
        footer = stringResource(R.string.settings_maps_footer),
    ) {
        Anchor("maps", highlight) {
            ToggleRow(
                title = stringResource(R.string.settings_maps),
                checked = maps,
                onCheckedChange = {
                    maps = it
                    prefs.showMaps = it
                },
                icon = LumenIcons.Location,
                iconColor = Green,
            )
        }
    }
    Group(stringResource(R.string.settings_group_videos)) {
        Anchor("autoplay", highlight) {
            ToggleRow(
                title = stringResource(R.string.settings_autoplay_videos),
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
                title = stringResource(R.string.settings_loop_videos),
                subtitle = stringResource(R.string.settings_start_over),
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
    Group(stringResource(R.string.settings_group_slideshow)) {
        Anchor("slide-time", highlight) {
            PickerRow(
                title = stringResource(R.string.settings_each_photo_for),
                options = SlideSeconds.map { pluralStringResource(R.plurals.settings_slide_seconds, it, it) },
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
                title = stringResource(R.string.settings_transition),
                options = listOf(stringResource(R.string.settings_transition_slide), stringResource(R.string.settings_transition_fade)),
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
                title = stringResource(R.string.settings_shuffle),
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
                title = stringResource(R.string.settings_repeat),
                subtitle = stringResource(R.string.settings_start_over),
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
        stringResource(R.string.settings_lumen_lock),
        footer = stringResource(R.string.settings_lumen_lock_footer),
    ) {
        Anchor("lumen-lock", highlight) {
            SettingsRow(
                title = stringResource(if (hiddenLockSet) R.string.settings_change_lumen_lock else R.string.settings_set_up_lumen_lock),
                onClick = onChangeLock,
                icon = LumenIcons.Lock,
                iconColor = Blue,
            )
        }
        if (hiddenLockSet) {
            Divider()
            Anchor("biometrics", highlight) {
                ToggleRow(
                    title = stringResource(R.string.settings_biometrics),
                    subtitle = stringResource(R.string.settings_biometrics_subtitle),
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
                title = stringResource(R.string.settings_app_lock),
                subtitle = stringResource(R.string.settings_app_lock_subtitle),
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
                    title = stringResource(R.string.settings_lock_after),
                    options = LockMinutes.map { if (it == 0) stringResource(R.string.settings_lock_immediately) else pluralStringResource(R.plurals.settings_lock_minutes, it, it) },
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
    // Encrypted under a PIN or pattern: honest about what that guards against (VaultKeyFile).
    val context = LocalContext.current
    val lockType = remember(encryptHidden) { com.subhashh.lumen.data.HiddenLockStore(context).type }
    val weakLock = encryptHidden && (lockType == com.subhashh.lumen.data.LockType.Pin || lockType == com.subhashh.lumen.data.LockType.Pattern)
    Group(stringResource(R.string.settings_hidden), note = if (weakLock) stringResource(R.string.settings_encrypt_weak_lock) else null) {
        Anchor("hidden", highlight) {
            SettingsRow(
                title = stringResource(R.string.settings_hidden),
                subtitle = stringResource(R.string.settings_hidden_subtitle),
                onClick = onOpenHidden,
                icon = LumenIcons.Hide,
                iconColor = Indigo,
            )
        }
        Divider()
        Anchor("encrypt", highlight) {
            ToggleRow(
                title = stringResource(R.string.settings_encrypt_hidden),
                subtitle = stringResource(R.string.settings_encrypt_hidden_subtitle),
                checked = encryptHidden,
                onCheckedChange = onToggleEncryptHidden,
                icon = LumenIcons.Shield,
                iconColor = Green,
            )
        }
        Divider()
        var screenshots by remember { mutableStateOf(prefs.hiddenScreenshots) }
        Anchor("hidden-screenshots", highlight) {
            ToggleRow(
                title = stringResource(R.string.settings_hidden_screenshots),
                subtitle = stringResource(R.string.settings_hidden_screenshots_subtitle),
                checked = screenshots,
                onCheckedChange = {
                    screenshots = it
                    prefs.hiddenScreenshots = it
                },
                icon = LumenIcons.Image,
                iconColor = Indigo,
            )
        }
    }
}

@Composable
private fun ColumnScope.PermissionsPage(status: PermissionStatus, highlight: String?) {
    val context = LocalContext.current
    Group(stringResource(R.string.settings_group_android_permissions), footer = stringResource(R.string.settings_permissions_footer)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Anchor("media-management", highlight) {
                SettingsRow(
                    title = stringResource(R.string.settings_media_management),
                    subtitle = stringResource(R.string.settings_media_management_subtitle),
                    value = stringResource(if (status.manageMedia) R.string.common_on else R.string.common_off),
                    onClick = { context.openSettings(Settings.ACTION_REQUEST_MANAGE_MEDIA) },
                    icon = LumenIcons.Shield,
                    iconColor = Green,
                )
            }
            Divider()
        }
        Anchor("all-files", highlight) {
            SettingsRow(
                title = stringResource(R.string.settings_all_files),
                subtitle = stringResource(R.string.settings_all_files_subtitle),
                value = stringResource(if (status.allFiles) R.string.common_on else R.string.common_off),
                onClick = { context.openSettings(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION) },
                icon = LumenIcons.FolderOpen,
                iconColor = Blue,
            )
        }
        Divider()
        Anchor("location", highlight) {
            SettingsRow(
                title = stringResource(R.string.settings_location_in_photos),
                subtitle = stringResource(R.string.settings_location_in_photos_subtitle),
                value = stringResource(if (status.mediaLocation) R.string.common_on else R.string.common_off),
                onClick = { context.openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) },
                icon = LumenIcons.Location,
                iconColor = Green,
            )
        }
    }
}

@Composable
private fun ColumnScope.BackupPage(highlight: String?, onExport: () -> Unit, onImport: () -> Unit, onImportFavorites: () -> Unit) {
    Group(stringResource(R.string.settings_group_lumen_backup), footer = stringResource(R.string.settings_backup_footer)) {
        Anchor("export", highlight) {
            SettingsRow(
                title = stringResource(R.string.settings_export_row),
                subtitle = stringResource(R.string.settings_export_subtitle),
                onClick = onExport,
                icon = LumenIcons.Export,
                iconColor = Green,
            )
        }
        Divider()
        Anchor("import", highlight) {
            SettingsRow(
                title = stringResource(R.string.settings_import_row),
                subtitle = stringResource(R.string.settings_import_subtitle),
                onClick = onImport,
                icon = LumenIcons.Import,
                iconColor = Blue,
            )
        }
    }
    Group(stringResource(R.string.settings_group_other_apps)) {
        Anchor("import-favorites", highlight) {
            SettingsRow(
                title = stringResource(R.string.settings_import_favorites_row),
                subtitle = stringResource(R.string.settings_import_favorites_subtitle),
                onClick = onImportFavorites,
                icon = LumenIcons.HeartFilled,
                iconColor = Pink,
            )
        }
    }
}

@Composable
private fun ColumnScope.AboutPage(
    prefs: LumenPrefs,
    highlight: String?,
    onOpenLicenses: () -> Unit,
    onConfirm: (com.subhashh.lumen.ui.components.Confirmation) -> Unit,
) {
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    val open = { url: String ->
        runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Unit
    }
    AboutHeader(version = context.versionName(), onDeveloper = { open(DeveloperUrl) })
    SoftwareUpdateGroup(highlight, onConfirm)
    Group(stringResource(R.string.settings_group_open_source), footer = stringResource(R.string.settings_open_source_footer)) {
        Anchor("github", highlight) {
            SettingsRow(
                title = stringResource(R.string.settings_github),
                value = "subhashhhhhh/Lumen",
                onClick = { open(RepoUrl) },
                icon = LumenIcons.Code,
                iconColor = Gray,
            )
        }
        Divider()
        Anchor("report-bug", highlight) {
            SettingsRow(
                title = stringResource(R.string.settings_report_bug),
                onClick = { open("$RepoUrl/issues") },
                icon = LumenIcons.Bug,
                iconColor = Red,
            )
        }
    }
    Group("Lumen") {
        Anchor("licenses", highlight) {
            SettingsRow(
                title = stringResource(R.string.settings_licenses),
                onClick = onOpenLicenses,
                icon = LumenIcons.Document,
                iconColor = Gray,
            )
        }
        Divider()
        Anchor("version", highlight) {
            SettingsRow(
                title = stringResource(R.string.settings_version),
                value = context.versionName(),
                // Seven taps show Developer Options, as on Android.
                onClick = {
                    taps++
                    if (taps == 7) {
                        val already = prefs.developerOptions
                        prefs.developerOptions = true
                        android.widget.Toast.makeText(
                            context,
                            context.getString(if (already) R.string.settings_developer_already_on else R.string.settings_developer_now_on),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                icon = LumenIcons.Info,
                iconColor = Gray,
            )
        }
        Divider()
        // The chip as Android reports it, and whether Lumen has NPU models for it: for bug reports.
        val chip = remember {
            val reported = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) android.os.Build.SOC_MODEL else android.os.Build.HARDWARE
            if (com.subhashh.lumen.ai.Models.npuChip(context) != null) context.getString(R.string.settings_chip_npu, reported) else reported
        }
        SettingsRow(
            title = stringResource(R.string.settings_chip),
            value = chip,
            onClick = null,
            icon = LumenIcons.Code,
            iconColor = Gray,
        )
    }
}

/** Lumen's icon, name and version, and who made it (their name opens their GitHub). */
@Composable
private fun AboutHeader(version: String, onDeveloper: () -> Unit) {
    val colors = groupColors()
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        com.subhashh.lumen.ui.components.AppIcon(96.dp)
        Spacer(Modifier.height(14.dp))
        Text("Lumen", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.text)
        Text(stringResource(R.string.settings_version_value, version), fontSize = 15.sp, color = colors.secondary)
        Spacer(Modifier.height(10.dp))
        val developerName = "Subhash Gottumukkala"
        val madeBy = stringResource(R.string.settings_made_by, developerName)
        val nameStart = madeBy.indexOf(developerName).coerceAtLeast(0)
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                append(madeBy.substring(0, nameStart))
                withStyle(androidx.compose.ui.text.SpanStyle(color = Blue, fontWeight = FontWeight.Medium)) { append(developerName) }
                append(madeBy.substring((nameStart + developerName.length).coerceAtMost(madeBy.length)))
            },
            fontSize = 15.sp,
            color = colors.secondary,
            modifier = Modifier.clickable(onClick = onDeveloper).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private const val DeveloperUrl = "https://github.com/subhashhhhhh"
private const val RepoUrl = "https://github.com/subhashhhhhh/Lumen"

@Composable
private fun ColumnScope.DeveloperPage(prefs: LumenPrefs, highlight: String?) {
    var details by remember { mutableStateOf(prefs.searchDetails) }
    var shown by remember { mutableStateOf(prefs.developerOptions) }
    Group(
        stringResource(R.string.common_search),
        footer = stringResource(R.string.settings_developer_search_footer),
    ) {
        Anchor("search-details", highlight) {
            ToggleRow(
                title = stringResource(R.string.settings_search_details),
                checked = details,
                onCheckedChange = {
                    details = it
                    prefs.searchDetails = it
                },
                icon = LumenIcons.Search,
                iconColor = Purple,
            )
        }
    }
    Group(stringResource(R.string.settings_group_onboarding), footer = stringResource(R.string.settings_onboarding_footer)) {
        Anchor("show-onboarding", highlight) {
            val context = LocalContext.current
            SettingsRow(
                title = stringResource(R.string.settings_show_onboarding),
                onClick = {
                    prefs.onboardingDone = false
                    (context as? android.app.Activity)?.recreate()
                },
                icon = LumenIcons.Restore,
                iconColor = Purple,
            )
        }
    }
    Group("", footer = stringResource(R.string.settings_developer_footer)) {
        Anchor("developer-options", highlight) {
            ToggleRow(
                title = stringResource(R.string.settings_show_developer_options),
                checked = shown,
                onCheckedChange = {
                    shown = it
                    prefs.developerOptions = it
                },
                icon = LumenIcons.Settings,
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
internal val Orange = Color(0xFFFF9F0A)
internal val Green = Color(0xFF30D158)
private val Red = Color(0xFFFF453A)
