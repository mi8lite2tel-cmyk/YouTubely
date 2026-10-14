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
    val Library = Toggle(R.drawable.ic_video_library, R.drawable.ic_video_library_fill1)
    val Subscriptions = Toggle(R.drawable.ic_subscriptions, R.drawable.ic_subscriptions_fill1)

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
    val PlaylistPlay = R.drawable.ic_playlist_play
    val FastForward = R.drawable.ic_fast_forward
    val FastRewind = R.drawable.ic_fast_rewind
    val Brightness = R.drawable.ic_brightness_medium
    val Volume = R.drawable.ic_volume_up
    val Subscribe = R.drawable.ic_person_add
    val Subscribed = R.drawable.ic_how_to_reg
    val SkipNext = R.drawable.ic_skip_next
    val MoreVert = R.drawable.ic_more_vert
    val DeleteSweep = R.drawable.ic_delete_sweep
    val HistoryOff = R.drawable.ic_history_toggle_off
    val Storage = R.drawable.ic_hard_drive
    val AspectRatio = R.drawable.ic_aspect_ratio
    val VideoFilled = R.drawable.ic_smart_display_fill1
    val Notifications = R.drawable.ic_notifications
    val NotificationsOff = R.drawable.ic_notifications_off
    val Battery = R.drawable.ic_battery_charging_full
    val PlaylistAdd = R.drawable.ic_playlist_add
    val PlaylistAddCheck = R.drawable.ic_playlist_add_check
    val QrCode = R.drawable.ic_qr_code_2
    val QrScanner = R.drawable.ic_qr_code_scanner
    val Upload = R.drawable.ic_file_upload
    val Add = R.drawable.ic_add
    val Edit = R.drawable.ic_edit
    val ForYou = R.drawable.ic_auto_awesome
    val Label = R.drawable.ic_label
    val Bookmark = R.drawable.ic_bookmark
    val BookmarkAdd = R.drawable.ic_bookmark_add
    val FolderOpen = R.drawable.ic_folder_open
    val ChevronRight = R.drawable.ic_chevron_right
    val DataUsage = R.drawable.ic_data_usage
    val Speed = R.drawable.ic_speed
    val Shield = R.drawable.ic_shield
    val Subtitles = R.drawable.ic_subtitles
    val Remove = R.drawable.ic_remove
    val Savings = R.drawable.ic_savings
    val Repeat = R.drawable.ic_repeat
    val RepeatOne = R.drawable.ic_repeat_one
}
