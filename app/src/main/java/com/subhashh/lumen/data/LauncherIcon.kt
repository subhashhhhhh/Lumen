package com.subhashh.lumen.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.subhashh.lumen.R

/**
 * The icons Lumen can wear on the Home screen. Android only lets an app change its icon by
 * switching between activity aliases declared in the manifest, each with its own icon: exactly one
 * is on at a time, and that choice lives in the system, not in Lumen's settings.
 *
 * Each entry matches an alias in AndroidManifest.xml and an icon drawn by
 * tools/icons/launcher_icons.py. The first keeps the name the launcher activity always had, so
 * Home screen shortcuts made before icons could change still work.
 */
enum class LauncherIcon(private val alias: String, @DrawableRes val icon: Int, @StringRes val label: Int) {
    FirstLight("MainActivity", R.mipmap.ic_launcher, R.string.app_icon_first_light),
    Dusk("icon.Dusk", R.mipmap.ic_launcher_dusk, R.string.app_icon_dusk),
    Ocean("icon.Ocean", R.mipmap.ic_launcher_ocean, R.string.app_icon_ocean),
    Forest("icon.Forest", R.mipmap.ic_launcher_forest, R.string.app_icon_forest),
    Rose("icon.Rose", R.mipmap.ic_launcher_rose, R.string.app_icon_rose),
    Graphite("icon.Graphite", R.mipmap.ic_launcher_graphite, R.string.app_icon_graphite),
    Bloom("icon.Bloom", R.mipmap.ic_launcher_bloom, R.string.app_icon_bloom),
    BloomDark("icon.BloomDark", R.mipmap.ic_launcher_bloom_dark, R.string.app_icon_bloom_dark),
    BloomOcean("icon.BloomOcean", R.mipmap.ic_launcher_bloom_ocean, R.string.app_icon_bloom_ocean),
    BloomSunset("icon.BloomSunset", R.mipmap.ic_launcher_bloom_sunset, R.string.app_icon_bloom_sunset);

    // The aliases are named in the code's package, whatever the build's application id is.
    private fun component(context: Context) = ComponentName(context.packageName, "com.subhashh.lumen.$alias")

    companion object {
        /** The icon that's on now: the one alias switched on, or the first when none was ever chosen. */
        fun current(context: Context): LauncherIcon {
            val packages = context.packageManager
            return entries.firstOrNull {
                it != FirstLight && packages.getComponentEnabledSetting(it.component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } ?: FirstLight
        }

        /**
         * Chosen in Settings but not on the Home screen yet. Android closes whatever was opened
         * through an alias the moment that alias is switched off, which would shut Lumen in the
         * user's face; so the switch waits until they leave ([applyPending]).
         */
        @Volatile
        private var pending: LauncherIcon? = null

        /** What Settings shows as chosen: the waiting choice, else the icon that's on. */
        fun chosen(context: Context): LauncherIcon = pending ?: current(context)

        fun choose(icon: LauncherIcon) {
            pending = icon
        }

        /** Called as Lumen leaves the screen: puts the waiting choice on the Home screen. */
        fun applyPending(context: Context) {
            val icon = pending ?: return
            pending = null
            // A launcher or a work profile that refuses leaves the old icon, which is all that can be done.
            if (icon != current(context)) runCatching { set(context, icon) }
        }

        /** Switches the Home screen to [icon]; the launcher redraws within a few seconds. */
        private fun set(context: Context, icon: LauncherIcon) {
            val packages = context.packageManager
            fun state(entry: LauncherIcon) =
                if (entry == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // All at once, so the launcher never sees two icons or none.
                packages.setComponentEnabledSettings(
                    entries.map { PackageManager.ComponentEnabledSetting(it.component(context), state(it), PackageManager.DONT_KILL_APP) },
                )
            } else {
                // The new one on before the old one off: never a moment without an icon.
                for (entry in listOf(icon) + (entries - icon)) {
                    packages.setComponentEnabledSetting(entry.component(context), state(entry), PackageManager.DONT_KILL_APP)
                }
            }
        }
    }
}
