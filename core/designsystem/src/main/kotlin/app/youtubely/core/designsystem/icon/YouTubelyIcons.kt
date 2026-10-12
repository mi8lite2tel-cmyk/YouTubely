// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.designsystem.icon

import androidx.annotation.DrawableRes
import app.youtubely.core.designsystem.R

/**
 * Material Symbols Rounded (Apache-2.0) as vector drawables. Icons with a `filled` variant are
 * used for selected states, where the outline morphs into the filled glyph.
 */
object YouTubelyIcons {
    class Toggle(
        @DrawableRes val outlined: Int,
        @DrawableRes val filled: Int,
    )

    val Home = Toggle(R.drawable.ic_home, R.drawable.ic_home_fill1)
    val Search = Toggle(R.drawable.ic_search, R.drawable.ic_search_fill1)
    val Settings = Toggle(R.drawable.ic_settings, R.drawable.ic_settings_fill1)
    val ThumbUp = Toggle(R.drawable.ic_thumb_up, R.drawable.ic_thumb_up_fill1)
    val Downloads = Toggle(R.drawable.ic_download, R.drawable.ic_download_fill1)

    val ArrowBack = R.drawable.ic_arrow_back
    val Close = R.drawable.ic_close
    val History = R.drawable.ic_history
    val NorthWest = R.drawable.ic_north_west
    val Play = R.drawable.ic_play_arrow_fill1
    val Pause = R.drawable.ic_pause_fill1
    val Replay10 = R.drawable.ic_replay_10
    val Forward10 = R.drawable.ic_forward_10
    val Fullscreen = R.drawable.ic_fullscreen
    val FullscreenExit = R.drawable.ic_fullscreen_exit
    val Tune = R.drawable.ic_tune
    val Visibility = R.drawable.ic_visibility
    val Share = R.drawable.ic_share
    val Error = R.drawable.ic_error
    val WifiOff = R.drawable.ic_wifi_off
    val Refresh = R.drawable.ic_refresh
    val Palette = R.drawable.ic_palette
    val DarkMode = R.drawable.ic_dark_mode
    val LightMode = R.drawable.ic_light_mode
    val Contrast = R.drawable.ic_contrast
    val BrightnessAuto = R.drawable.ic_brightness_auto
    val Music = R.drawable.ic_music_note
    val Gaming = R.drawable.ic_sports_esports
    val Live = R.drawable.ic_live_tv
    val Podcasts = R.drawable.ic_podcasts
    val Movie = R.drawable.ic_movie
    val Trending = R.drawable.ic_trending_up
    val ExpandMore = R.drawable.ic_expand_more
    val ExpandLess = R.drawable.ic_expand_less
    val Check = R.drawable.ic_check
    val Verified = R.drawable.ic_check_circle_fill1
    val Language = R.drawable.ic_language
    val Info = R.drawable.ic_info
    val Schedule = R.drawable.ic_schedule
    val Video = R.drawable.ic_smart_display
    val Download = R.drawable.ic_download
    val DownloadDone = R.drawable.ic_download_done
    val Delete = R.drawable.ic_delete
    val PauseOutline = R.drawable.ic_pause
    val Headphones = R.drawable.ic_headphones
    val Wifi = R.drawable.ic_wifi
}
