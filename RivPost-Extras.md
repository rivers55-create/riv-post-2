# Riv Post: extra features on top of official Telegram

The plan: start from **official Telegram's own code**, rename it **Riv Post** (own name and icon), keep everything looking and working exactly like official Telegram, then add every feature below.

Rules for all of them:
- Every extra is **optional**, with its own switch in Settings.
- When official Telegram already has a feature, the **official way is the default**. Riv Post's version is an option you can switch to.
- **Arabic and English**, with right-to-left Arabic.
- Nothing is named after other companies.
- Personal use, private repo.

Sorted from biggest to smallest. ✅ = already built in the current Riv Post (2.2.5) and needs moving over. 🆕 = asked for but not built yet.

---

## 1. Music player (full RTP-level player inside Riv Post)

The music player should match **Riv Tunes Player (RTP)**, with Telegram chats as music sources as well as phone files.

**Library and sources**
- ✅ Songs from chosen Telegram chats and channels (music sources), plus phone files
- ✅ Library tabs that can be reordered and hidden (songs, albums, artists, playlists…)
- ✅ Playlists: create, edit, add from chat
- ✅ "Save to music" on any audio message
- ✅ Hide songs, hide whole chats from the library, hide short clips (minimum length)
- ✅ Pre-download next songs (Wi-Fi only option)
- From RTP: smart playlists, favourites, history, stats, duplicate finder, exclude folders
- From RTP: tag editor (single and batch), cover art editing
- From RTP: online artist pictures and covers (Wi-Fi only option)

**Playback**
- ✅ Background playback with the phone's media panel (Samsung-style card, inside and outside the app)
- ✅ FFmpeg decoding for any format
- ✅ Shuffle, repeat, reverse queue, "Now playing" queue
- ✅ A–B repeat
- ✅ Speed and pitch
- ✅ Equalizer and bass boost
- ✅ Skip buttons with custom seconds
- ✅ Lyrics (synced), with online lyrics as an option
- From RTP: gapless, crossfade, ReplayGain, sleep timer

**Look**
- ✅ Full player screen, wavy seek bar option, background styles, glow
- ✅ Mini player: compact card or small bar, can be resized and dragged, top or bottom
- ✅ Music drawer and its own tab in the bottom bar
- From RTP: 4 layouts (Side menu, Floating, Classic, Stream), header styles, player styles, Immersive album page, Quick looks
- From RTP: home-screen widgets (Mini and Shelf), Android Auto, backup and restore

**In chats** 🆕
- Playing a song in a chat shows the **official Telegram music bar** under the chat header, but it controls the Riv Post music player (same queue, same media panel).
- Tapping a song toggles play and pause (it doesn't restart).

---

## 2. Video library and player (RVP-level)

**Video library**
- ✅ Videos tab collecting videos from chosen chats and channels (video sources), with a minimum length filter
- ✅ Big banner (hero) at the top, rows, "My List", Continue watching with saved positions
- ✅ Title cleanup (messy file names become clean titles)
- ✅ TMDb info: posters, ratings, descriptions, genres, runtime, cast
- ✅ TheTVDB as a second source for shows; choose which is checked first
- ✅ Episode detection (S02E05 becomes "Season 2, Episode 5" with the real episode name and still)
- ✅ Details page: backdrop, description, episode block, cast with photos
- ✅ Saved info cache, with a "clear" button
- ✅ IPTV playlists (M3U)
- ✅ Network shares (SMB)
- ✅ Swipe between library tabs

**Video player** (the Riv Post player, matching RVP)
- ✅ Hardware or software decoding, plus FFmpeg
- ✅ Subtitles: load files, size, background, colour
- ✅ Picture-in-picture, auto PiP, background play
- ✅ Phone media panel for videos
- ✅ A–B repeat
- From RVP: swipe gestures (brightness, volume, seek), seek preview, audio-only mode, playlists, equalizer, downloads of direct links, Glass controls option, remember rotation, private folder, Chromecast

**In chats** 🆕
- Videos in chats play the **official Telegram way** by default.
- A "Play in Riv Post player" button in the viewer, plus a setting to always use it.
- In the Videos tab, IPTV and network shares, the Riv Post player stays.

---

## 3. Call extras (on top of official calls, video calls and screen share)

**During a call**
- ✅ Record calls (both sides, saved to Downloads › Riv Post › Calls), auto-record per person
- ✅ Hold with hold music (built-in tune or your own file)
- ✅ Play audio into the call (a song or a sound; sends only the clip while you're muted)
- ✅ Mute both ways
- ✅ Voice changer: deep, high, robot
- ✅ Live captions of their voice (Android 13+), optional translation
- ✅ Call notes and captions saved to Saved Messages after the call
- ✅ Chat box over the call screen
- ✅ Call details: network, signal, data used, data saving
- ✅ Mic level (whisper boost), their volume, bass and treble
- ✅ "You're muted" reminder, weak-connection warning
- ✅ Volume-down twice to mute, shake to hang up
- ✅ Call time alert every X minutes
- ✅ Vibrate on connect and hang-up
- ✅ Call screen styles: colours, or their photo as the background

**Incoming calls**
- ✅ Decline with a quick message (editable list), decline and remind later (10 min / 30 min / 1 h / 3 h)
- ✅ Flip phone face down to silence
- ✅ Silent ring for unknown callers
- ✅ Quiet hours: ring silently or decline with an auto-reply; "Ring at night" favourites still ring
- ✅ Call waiting: see a second call and switch to it
- ✅ Missed-call notification with Call back / In 10 min / In 1 hour
- ✅ Their last message shown while it rings
- ✅ Auto-answer: per person, or whenever headphones are connected
- ✅ Headset button: press to answer or mute, hold to hang up
- ✅ Ringtone per person, speaker on per person

**Outgoing and other**
- ✅ Auto-redial (1–5 tries)
- ✅ Wi-Fi only calls
- ✅ Battery and data saver
- ✅ Call button in message notifications
- ✅ Car and Bluetooth mode (works like a normal phone call)
- ✅ Hide Vault calls from history; hide or delete single entries
- ✅ Talk time stats (week and month, top people)
- ✅ Direct-connection switch (hide your IP)

**Screen share** 🆕
- Phone sound on or off at any time during the share
- Mic on or off separately
- Share sound from one app only
- Never share notification sounds
- Hide the picture but keep the sound
- Show taps, auto-stop timer, pause the share for chosen apps, pause when the phone locks
- Share only part of the screen, floating bubble to pause, mute or stop, save a copy of your own share

**Video calls** 🆕
- Background blur or replacement, low-light boost
- Emoji face mask, auto-framing
- Flashlight button, snapshot of their video, freeze your camera
- Zoom, mirror, fit or fill
- Turn their video off for yourself, low-data video
- Split screen (video plus chat)
- Record video calls

**Group calls and voice chats** 🆕
- Join muted or listen only
- Speaker history (who spoke and when)
- Record group calls on your phone (not only admins)
- Captions in group calls
- Listen in the background like a podcast
- Raise-hand alert sound

---

## 4. Privacy and hidden chats

- ✅ **Vault (hidden chats):** separate PIN or fingerprint
- ✅ App lock: PIN, fingerprint, lock delay, block screenshots
- ✅ **Ghost mode:** read without sending read receipts or online status
- ✅ Read freeze (messages stay unread for others until you choose)
- ✅ **Keep deleted messages** (anti-delete)
- ✅ **Keep edit history** of edited messages
- ✅ Hidden chats list
- ✅ Local pins (pin chats only on this phone, without the limit)
- ✅ Secure chats (extra lock per chat)
- ✅ Forward without the source name (clean forward)
- ✅ "Show that I'm typing" on or off

---

## 5. Customise (looks, on top of the official look)

Official Telegram's look is the **default**. All of these are options.
- ✅ Bottom bar: on or off, position, labels, which tabs (Contacts, Calls, Profile, Music, Videos)
- ✅ Folder tabs: style, position, counts, count muted chats
- ✅ Title style, logo, custom app title, floating button
- ✅ Avatars: shape, size, group avatars, online dot
- ✅ Chat list: density, preview lines, media thumbnails, dividers
- ✅ Bubbles: radius, tail, shape, shadow, border, glow
- ✅ Every colour: accent, background, top bar, bottom bar, bubbles in and out, text, links, titles, badges
- ✅ Styles: OLED black, flat, glass
- ✅ Wallpaper: blur, dim, tint, accent from wallpaper
- ✅ Header style, input bar style, wave style, scroll feel, haptics
- ✅ Custom fonts, text size, system emoji option, sticker size
- ✅ Side drawer background or image
- ✅ App icon choice
- ✅ Scheduled night mode (from–to)
- ✅ Reduce motion, power saving

---

## 6. Chat and message extras

- ✅ Tap empty space to select, long-press select, **range select** button
- ✅ Swipe left to reply, swipe right to **translate** (optional swipe to forward instead)
- ✅ Double-tap for quick reaction (choose the reaction)
- ✅ Auto-translate chosen chats, translate button
- ✅ Live formatting while typing (markdown), format chips
- ✅ Select any part of a message's text
- ✅ Edit links before sending
- ✅ Code blocks with colours, simple math formulas
- ✅ Big emoji, animated emoji, loop stickers
- ✅ Quick forward to a favourite chat
- ✅ Reply in another chat, reply options sheet
- ✅ Seen-by row, read dates, poll voters
- ✅ Mention suggestions, drafts shown in the list
- ✅ Hashtags and mentions clickable, hashtag search
- ✅ Plain view for channel posts
- ✅ Hide keyboard on scroll
- ✅ Jump to date, jump to mentions and reactions
- ✅ Mixed Arabic and English text shown in the right direction
- ✅ Comments button and forum topics view options
- ✅ Stories row on or off

---

## 7. Media and camera extras

- ✅ Photo editor before sending
- ✅ Built-in camera
- ✅ Video messages: round, square crop, recorder
- ✅ Send as album, compress video
- ✅ Save folder choice, save by type (photos, videos, music, files)
- ✅ Auto-download rules: mobile, roaming, video and file size limits
- ✅ Autoplay small videos in chat
- ✅ Media gallery per chat, zoomable photos, profile photo history
- ✅ Map previews for locations, pick a place on a map
- ✅ Earpiece for voice messages
- ✅ GIF button

---

## 8. Accounts, connection and notifications

- ✅ Multiple accounts, swipe between accounts
- ✅ Proxy list with auto-switch to a working proxy
- ✅ Your own Firebase push (optional)
- ✅ Keep-alive connection for messages; "Hide this notice" for the connection notification
- ✅ QR login (show and scan), country picker
- ✅ Debug log (app and TDLib), send and clear, crash capture
- 🆕 Engine crash reports included in the debug log
- 🆕 "Send debug log" on the main Settings page
- 🆕 Notifications as official Telegram, plus extras: chat bubbles, priority conversations, uploaded sounds, in-app sound and vibration

---

## 9. Small extras

- ✅ Language packs: translated language files saved per language
- ✅ Instant View pages
- ✅ Stats screens, admin log, invite links, join requests
- ✅ Sticker pack manager (add, remove, reorder)
- ✅ Data usage screen, storage screen
- ✅ Blocked users, notification exceptions, notification defaults
- ✅ Emoji status picker, wallpapers screen
- ✅ Two-step password and password recovery
- ✅ Suggested folders, share folder links
- ✅ "Mark all as read"

---

## Not possible (same for every app built from Telegram's code)

- Buying Premium or Stars inside the app (Telegram's Google Play account)
- Telegram's own push service (only official apps); Riv Post uses keep-alive or your own Firebase
- Passkey login
- Chromecast and the Windows version are **on hold** (your choice)
