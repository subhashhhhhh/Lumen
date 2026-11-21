package com.subhashh.lumen.ui.icons

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The icons Lumen draws, in the set of the current style (Settings → Appearance → Style):
 * Fluent for Lumen's own look, Material Symbols for Material 3 Expressive. Each is read when
 * it's drawn, so everything on screen changes set with the style.
 */
object LumenIcons {
    /** Set by the theme. Snapshot state: whatever read an icon is drawn again when it changes. */
    var material by mutableStateOf(false)

    val Library: ImageVector get() = if (material) MaterialIcons.Library else FluentIcons.Library
    val LibraryFilled: ImageVector get() = if (material) MaterialIcons.LibraryFilled else FluentIcons.LibraryFilled
    val Collections: ImageVector get() = if (material) MaterialIcons.Collections else FluentIcons.Collections
    val CollectionsFilled: ImageVector get() = if (material) MaterialIcons.CollectionsFilled else FluentIcons.CollectionsFilled
    val Search: ImageVector get() = if (material) MaterialIcons.Search else FluentIcons.Search
    val SearchFilled: ImageVector get() = if (material) MaterialIcons.SearchFilled else FluentIcons.SearchFilled
    val Back: ImageVector get() = if (material) MaterialIcons.Back else FluentIcons.Back
    val Share: ImageVector get() = if (material) MaterialIcons.Share else FluentIcons.Share
    val Heart: ImageVector get() = if (material) MaterialIcons.Heart else FluentIcons.Heart
    val HeartFilled: ImageVector get() = if (material) MaterialIcons.HeartFilled else FluentIcons.HeartFilled
    val Info: ImageVector get() = if (material) MaterialIcons.Info else FluentIcons.Info
    val Delete: ImageVector get() = if (material) MaterialIcons.Delete else FluentIcons.Delete
    val DeleteForever: ImageVector get() = if (material) MaterialIcons.DeleteForever else FluentIcons.DeleteForever
    val Restore: ImageVector get() = if (material) MaterialIcons.Restore else FluentIcons.Restore
    val Play: ImageVector get() = if (material) MaterialIcons.Play else FluentIcons.Play
    val Pause: ImageVector get() = if (material) MaterialIcons.Pause else FluentIcons.Pause
    val Folder: ImageVector get() = if (material) MaterialIcons.Folder else FluentIcons.Folder
    val Video: ImageVector get() = if (material) MaterialIcons.Video else FluentIcons.Video
    val Screenshot: ImageVector get() = if (material) MaterialIcons.Screenshot else FluentIcons.Screenshot
    val Camera: ImageVector get() = if (material) MaterialIcons.Camera else FluentIcons.Camera
    val Image: ImageVector get() = if (material) MaterialIcons.Image else FluentIcons.Image
    val Location: ImageVector get() = if (material) MaterialIcons.Location else FluentIcons.Location
    val Selected: ImageVector get() = if (material) MaterialIcons.Selected else FluentIcons.Selected
    val Unselected: ImageVector get() = if (material) MaterialIcons.Unselected else FluentIcons.Unselected
    val Close: ImageVector get() = if (material) MaterialIcons.Close else FluentIcons.Close
    val Filter: ImageVector get() = if (material) MaterialIcons.Filter else FluentIcons.Filter
    val Settings: ImageVector get() = if (material) MaterialIcons.Settings else FluentIcons.Settings
    val Check: ImageVector get() = if (material) MaterialIcons.Check else FluentIcons.Check
    val More: ImageVector get() = if (material) MaterialIcons.More else FluentIcons.More
    val Add: ImageVector get() = if (material) MaterialIcons.Add else FluentIcons.Add
    val NewAlbum: ImageVector get() = if (material) MaterialIcons.NewAlbum else FluentIcons.NewAlbum
    val Lock: ImageVector get() = if (material) MaterialIcons.Lock else FluentIcons.Lock
    val Unhide: ImageVector get() = if (material) MaterialIcons.Unhide else FluentIcons.Unhide
    val Pin: ImageVector get() = if (material) MaterialIcons.Pin else FluentIcons.Pin
    val Sound: ImageVector get() = if (material) MaterialIcons.Sound else FluentIcons.Sound
    val Muted: ImageVector get() = if (material) MaterialIcons.Muted else FluentIcons.Muted
    val DarkTheme: ImageVector get() = if (material) MaterialIcons.DarkTheme else FluentIcons.DarkTheme
    val Accessibility: ImageVector get() = if (material) MaterialIcons.Accessibility else FluentIcons.Accessibility
    val Hdr: ImageVector get() = if (material) MaterialIcons.Hdr else FluentIcons.Hdr
    val Repeat: ImageVector get() = if (material) MaterialIcons.Repeat else FluentIcons.Repeat
    val Timer: ImageVector get() = if (material) MaterialIcons.Timer else FluentIcons.Timer
    val SlideTransition: ImageVector get() = if (material) MaterialIcons.SlideTransition else FluentIcons.SlideTransition
    val Shuffle: ImageVector get() = if (material) MaterialIcons.Shuffle else FluentIcons.Shuffle
    val Fingerprint: ImageVector get() = if (material) MaterialIcons.Fingerprint else FluentIcons.Fingerprint
    val Shield: ImageVector get() = if (material) MaterialIcons.Shield else FluentIcons.Shield
    val FolderOpen: ImageVector get() = if (material) MaterialIcons.FolderOpen else FluentIcons.FolderOpen
    val Export: ImageVector get() = if (material) MaterialIcons.Export else FluentIcons.Export
    val Import: ImageVector get() = if (material) MaterialIcons.Import else FluentIcons.Import
    val Bug: ImageVector get() = if (material) MaterialIcons.Bug else FluentIcons.Bug
    val Code: ImageVector get() = if (material) MaterialIcons.Code else FluentIcons.Code
    val Document: ImageVector get() = if (material) MaterialIcons.Document else FluentIcons.Document
    val FolderProhibited: ImageVector get() = if (material) MaterialIcons.FolderProhibited else FluentIcons.FolderProhibited
    val Hide: ImageVector get() = if (material) MaterialIcons.Hide else FluentIcons.Hide
    val Edit: ImageVector get() = if (material) MaterialIcons.Edit else FluentIcons.Edit
    val LiveText: ImageVector get() = if (material) MaterialIcons.LiveText else FluentIcons.LiveText
    val LiveTextFilled: ImageVector get() = if (material) MaterialIcons.LiveTextFilled else FluentIcons.LiveTextFilled
    val Subtitles: ImageVector get() = if (material) MaterialIcons.Subtitles else FluentIcons.Subtitles
    val Face: ImageVector get() = if (material) MaterialIcons.Face else FluentIcons.Face
    val Crop: ImageVector get() = if (material) MaterialIcons.Crop else FluentIcons.Crop
    val RotateLeft: ImageVector get() = if (material) MaterialIcons.RotateLeft else FluentIcons.RotateLeft
    val Flip: ImageVector get() = if (material) MaterialIcons.Flip else FluentIcons.Flip
    val Adjust: ImageVector get() = if (material) MaterialIcons.Adjust else FluentIcons.Adjust
    val Filters: ImageVector get() = if (material) MaterialIcons.Filters else FluentIcons.Filters
    val Globe: ImageVector get() = if (material) MaterialIcons.Globe else FluentIcons.Globe
    val Translate: ImageVector get() = if (material) MaterialIcons.Translate else FluentIcons.Translate
    val History: ImageVector get() = if (material) MaterialIcons.History else FluentIcons.Restore

    /** A plain chevron pointing left; turned to point anywhere. (Lumen's Back is one; Material's is an arrow.) */
    val Chevron: ImageVector get() = if (material) MaterialIcons.Chevron else FluentIcons.Back
    val Palette: ImageVector get() = if (material) MaterialIcons.Palette else FluentIcons.Filters
    val Sort: ImageVector get() = if (material) MaterialIcons.Sort else FluentIcons.Filter
}
