package com.subhashh.lumen.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.subhashh.lumen.ui.theme.Lumen
import org.junit.Assert.assertEquals
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.subhashh.lumen.data.CollectionKeys
import com.subhashh.lumen.data.MediaCollection
import com.subhashh.lumen.ui.collections.CollectionsScreen
import androidx.compose.ui.platform.LocalContext
import com.subhashh.lumen.data.HiddenLockStore
import com.subhashh.lumen.ui.albums.AlbumPicker
import com.subhashh.lumen.ui.components.ConfirmDialog
import com.subhashh.lumen.ui.components.LocalMaterialBar
import com.subhashh.lumen.ui.components.LocalOnBar
import com.subhashh.lumen.ui.components.MaterialBarState
import com.subhashh.lumen.ui.components.MaterialNavBar
import com.subhashh.lumen.ui.components.MaterialTopBar
import com.subhashh.lumen.ui.components.MaterialZoomSwitch
import com.subhashh.lumen.ui.settings.AccentRow
import com.subhashh.lumen.ui.theme.AccentPresets
import com.subhashh.lumen.ui.components.DateTimeDialog
import com.subhashh.lumen.ui.components.ProgressRing
import com.subhashh.lumen.ui.components.Spinner
import com.subhashh.lumen.ui.hidden.HiddenLockScreen
import com.subhashh.lumen.ui.hidden.LockFlow
import com.subhashh.lumen.ui.library.LibraryHeader
import com.subhashh.lumen.ui.library.SelectionMark
import com.subhashh.lumen.ui.onboarding.Onboarding
import com.subhashh.lumen.ui.settings.LicensesScreen
import com.subhashh.lumen.ui.settings.ResizeDialog
import com.subhashh.lumen.ui.components.Confirmation
import com.subhashh.lumen.ui.components.GlassIconButton
import com.subhashh.lumen.ui.components.GlassMenu
import com.subhashh.lumen.ui.components.GlassTabBar
import com.subhashh.lumen.ui.components.GlassTextButton
import com.subhashh.lumen.ui.components.LumenTab
import com.subhashh.lumen.ui.components.MenuItem
import com.subhashh.lumen.ui.components.RenameDialog
import com.subhashh.lumen.ui.editor.EditorChip
import com.subhashh.lumen.ui.editor.EditorIconButton
import com.subhashh.lumen.ui.editor.EditorSlider
import com.subhashh.lumen.ui.editor.EditorTabs
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.library.ZoomLevel
import com.subhashh.lumen.ui.search.SearchField
import com.subhashh.lumen.ui.selection.SelectionActionBar
import com.subhashh.lumen.ui.settings.Blue
import com.subhashh.lumen.ui.settings.Divider
import com.subhashh.lumen.ui.settings.Green
import com.subhashh.lumen.ui.settings.Group
import com.subhashh.lumen.ui.settings.Indigo
import com.subhashh.lumen.ui.settings.LargeTitle
import com.subhashh.lumen.ui.settings.NumberRow
import com.subhashh.lumen.ui.settings.Orange
import com.subhashh.lumen.ui.settings.PickerRow
import com.subhashh.lumen.ui.settings.SettingsPage
import com.subhashh.lumen.ui.settings.SettingsRow
import com.subhashh.lumen.ui.settings.ToggleRow
import com.subhashh.lumen.ui.theme.AlwaysDark
import com.subhashh.lumen.ui.theme.DesignStyle
import com.subhashh.lumen.ui.theme.LocalStyle
import com.subhashh.lumen.ui.theme.MaterialPalette
import com.subhashh.lumen.ui.theme.LumenTheme
import com.subhashh.lumen.ui.theme.StyleSetting
import com.subhashh.lumen.ui.transition.CoverTransition
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws Lumen's shared components in each style (Settings → Appearance → Style), light and dark,
 * off the phone. It fails if anything throws while composing or drawing, or draws nothing; the
 * pictures land in app/build/style-shots for a look.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class StyleRenderTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private class Look(
        val name: String,
        val style: DesignStyle,
        val dark: Boolean,
        val accent: Color? = null,
        val palette: MaterialPalette = MaterialPalette.Tonal,
        val pureBlack: Boolean = false,
    )

    private val looks = listOf(
        Look("lumen-dark", DesignStyle.Lumen, dark = true),
        Look("lumen-light", DesignStyle.Lumen, dark = false),
        Look("material-dark", DesignStyle.Material, dark = true),
        Look("material-light", DesignStyle.Material, dark = false),
        Look("material-pink-black", DesignStyle.Material, dark = true, accent = Color(0xFFFF4F9A), palette = MaterialPalette.Expressive, pureBlack = true),
        Look("material-fruit-light", DesignStyle.Material, dark = false, accent = Color(0xFF2FB344), palette = MaterialPalette.FruitSalad),
    )

    /** Draws [content] in every look, saving "<screen>-<look>.png". */
    private fun render(screen: String, content: @Composable () -> Unit) {
        for (look in looks) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            controller.get().setContent {
                CompositionLocalProvider(LocalStyle provides StyleSetting(look.style, accent = look.accent, palette = look.palette, pureBlack = look.pureBlack)) {
                    LumenTheme(darkTheme = look.dark) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                    }
                }
            }
            compose.waitForIdle()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("$screen in ${look.name} drew a single flat colour", pixels.distinct().size > 3)
            val out = File("build/style-shots").apply { mkdirs() }
            File(out, "$screen-${look.name}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            controller.pause().stop().destroy()
        }
    }

    /** Changing the style re-themes the screens in place: nothing they remember is lost. */
    @Test
    fun switchingStyleKeepsWhatScreensRemember() {
        var style by mutableStateOf(DesignStyle.Lumen)
        val remembered = mutableListOf<Any>()
        val material = mutableListOf<Boolean>()
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
            CompositionLocalProvider(LocalStyle provides StyleSetting(style)) {
                LumenTheme(darkTheme = true) {
                    val token = remember { Any() }
                    val isMaterial = Lumen.material
                    SideEffect {
                        remembered += token
                        material += isMaterial
                    }
                }
            }
        }
        compose.waitForIdle()
        style = DesignStyle.Material
        compose.waitForIdle()
        style = DesignStyle.Lumen
        compose.waitForIdle()
        assertEquals(listOf(false, true, false), material)
        assertEquals("the content was rebuilt on a style change", 1, remembered.distinct().size)
    }

    @Test
    fun settings() = render("settings") {
        SettingsPage(PaddingValues(top = 24.dp, bottom = 24.dp)) {
            LargeTitle("Appearance")
            Group("Display") {
                PickerRow("Theme", listOf("Match Phone", "Light", "Dark"), selected = 0, onSelect = {}, icon = LumenIcons.DarkTheme, iconColor = Indigo)
                Divider()
                ToggleRow("Reduce Motion", checked = true, onCheckedChange = {}, subtitle = "No flying photos, springs or bounces", icon = LumenIcons.Accessibility, iconColor = Blue)
                Divider()
                ToggleRow("Film Strip", checked = false, onCheckedChange = {}, icon = LumenIcons.Image, iconColor = Green)
            }
            Group("Style", footer = "Lumen is the app's own look.") {
                SettingsRow("Software Update", value = "0.4.0", onClick = {}, icon = LumenIcons.Import, iconColor = Orange)
                Divider()
                NumberRow("Frames a Second", value = 2, onValueChange = {}, min = 1, max = 60, unit = "fps")
            }
            Group("") {
                SettingsRow("Licences", onClick = {})
            }
        }
    }

    @Test
    fun bars() = render("bars") {
        val backdrop = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color(0xFF7A8F6B)))
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(backdrop, LumenIcons.Back, "Back", {})
                    GlassIconButton(backdrop, LumenIcons.Settings, "Settings", {}, badge = true)
                    GlassTextButton(backdrop, "Select All", {})
                }
                SearchField(backdrop, query = "", onQueryChange = {}, autoFocus = false, onAutoFocused = {}, onSubmit = {})
                GlassTabBar(backdrop, selected = LumenTab.Library, onSelect = {}, searchSelected = false, onSearch = {})
                GlassTabBar(backdrop, selected = LumenTab.Collections, onSelect = {}, searchSelected = true, onSearch = {})
                GlassTabBar(
                    backdrop, selected = LumenTab.Library, onSelect = {}, searchSelected = false, onSearch = {},
                    zoomLevel = ZoomLevel.Months, showZoom = true,
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SelectionActionBar(
                        backdrop, hasSelection = true, inTrash = false, inHidden = false, allFavorite = true, showFavorite = true,
                        onShare = {}, onFavorite = {}, onDelete = {}, onRestore = {}, onDeleteForever = {}, onUnhide = {}, onMore = {},
                    )
                }
            }
        }
    }

    /** Material's own frame: the top app bar with its Years / Months / All row, and the navigation bar. */
    @Test
    fun chrome() = render("chrome") {
        val bar = remember { MaterialBarState() }
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalMaterialBar provides bar.takeIf { Lumen.material }) {
                Column(Modifier.padding(top = 150.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LibraryHeader("Library", "October 2026", topPadding = 8.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SelectionMark(selected = true)
                        SelectionMark(selected = false)
                    }
                    AccentRow(accent = AccentPresets[2], onAccent = {})
                }
            }
            if (Lumen.material) {
                MaterialTopBar(bar, statusTop = 24.dp, pushed = false, untitled = false, below = {
                    MaterialZoomSwitch(ZoomLevel.All, {}, Modifier.padding(start = 16.dp, bottom = 12.dp))
                })
                CompositionLocalProvider(LocalOnBar provides true) {
                    val backdrop = rememberLayerBackdrop()
                    Row(Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 16.dp)) {
                        GlassIconButton(backdrop, LumenIcons.Filter, "View", {})
                        GlassIconButton(backdrop, LumenIcons.Settings, "Settings", {}, badge = true)
                    }
                }
                MaterialNavBar(LumenTab.Library, {}, searchSelected = false, onSearch = {}, navBottom = 24.dp, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    @Test
    fun menu() = render("menu") {
        val backdrop = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color(0xFF7A8F6B)))
            GlassMenu(
                backdrop, open = true, onDismiss = {},
                sections = listOf(
                    listOf(MenuItem("All Items", checked = true), MenuItem("Favourites"), MenuItem("Videos")),
                    listOf(MenuItem("Sort", value = "Date", submenu = emptyList()), MenuItem("Delete", destructive = true)),
                ),
                origin = TransformOrigin(1f, 0f),
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
            )
        }
    }

    @Test
    fun confirm() = render("confirm") {
        ConfirmDialog(
            Confirmation("Delete 3 Photos?", "They go to Recently Deleted for 30 days.", confirm = "Delete", destructive = true) {},
            onDismiss = {},
        )
    }

    @Test
    fun rename() = render("rename") {
        RenameDialog(currentName = "IMG_2041.jpg", onRename = {}, onDismiss = {})
    }

    @Test
    fun collections() = render("collections") {
        val utilities = listOf(
            MediaCollection(CollectionKeys.Favorites, "Favourites", null, 128),
            MediaCollection(CollectionKeys.Videos, "Videos", null, 41),
            MediaCollection(CollectionKeys.Screenshots, "Screenshots", null, 902),
            MediaCollection(CollectionKeys.RecentlyDeleted, "Recently Deleted", null, 4),
        )
        val folders = listOf("Camera", "WhatsApp Images", "Downloads", "Pictures").mapIndexed { i, name ->
            MediaCollection(CollectionKeys.folder(i.toLong()), name, null, 10 * (i + 1), relativePath = "DCIM/$name/")
        }
        CollectionsScreen(
            title = "Collections", utilities = utilities, hiddenLockSet = false, folders = folders, people = emptyList(),
            gridState = rememberLazyGridState(), contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp), onOpen = {},
            columns = 2, onColumnsChange = {}, selection = null, onSelectionChange = {},
            coverTransition = remember { CoverTransition() },
        )
    }

    @Test
    fun dateTime() = render("datetime") {
        // A fixed moment, so the picture is the same on every run.
        DateTimeDialog(original = 1_760_000_000_000L, count = 3, onAdjust = {}, onDismiss = {})
    }

    @Test
    fun resize() = render("resize") {
        ResizeDialog(count = 2, onSave = { _, _, _ -> }, onDismiss = {})
    }

    @Test
    fun albumPicker() = render("albumpicker") {
        val albums = listOf("Camera", "Screenshots", "Downloads").mapIndexed { i, name ->
            MediaCollection(CollectionKeys.folder(i.toLong()), name, null, 10 * (i + 1), relativePath = "DCIM/$name/")
        }
        AlbumPicker(open = true, title = "Move to Album", albums = albums, onPick = {}, onDismiss = {}, newAlbumPath = { it })
    }

    @Test
    fun lock() = render("lock") {
        val context = LocalContext.current
        HiddenLockScreen(LockFlow.Setup, remember { HiddenLockStore(context) }, onDone = {}, onCancel = {}, onForgot = {})
    }

    @Test
    fun onboarding() = render("onboarding") {
        Onboarding(hasAccess = true, onRequestAccess = {}, photos = emptyList(), onFinish = {})
    }

    @Test
    fun licenses() = render("licenses") {
        LicensesScreen(PaddingValues(top = 24.dp, bottom = 24.dp))
    }

    @Test
    fun marks() = render("marks") {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LibraryHeader("Library", "October 2026", topPadding = 8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SelectionMark(selected = true)
                SelectionMark(selected = false)
                Spinner()
                ProgressRing({ 0.6f })
            }
        }
    }

    @Test
    fun editor() = render("editor") {
        AlwaysDark {
            Column(
                Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EditorIconButton(LumenIcons.RotateLeft, "Rotate", {})
                    EditorIconButton(LumenIcons.Flip, "Flip", {}, active = true)
                    EditorChip("Original", selected = true, onClick = {})
                    EditorChip("Square", selected = false, onClick = {})
                    EditorChip("Exposure", selected = false, onClick = {}, detail = "+12")
                }
                EditorSlider(0.4f, {})
                EditorSlider(-0.6f, {})
                EditorSlider(0.3f, {}, min = 0f, max = 1f)
                EditorTabs(listOf("Crop", "Adjust", "Filters"), "Adjust", icon = { LumenIcons.Adjust }, label = { it }, onSelect = {})
            }
        }
    }
}
