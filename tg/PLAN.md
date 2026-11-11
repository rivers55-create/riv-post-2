# Riv Post on official Telegram: work plan

The full feature list is in `RivPost-Extras.md` on the `main` branch.

## Where things are
- **Branch `tg`:** the new app. Telegram's source is not stored here. The workflow `.github/workflows/tg.yml` downloads Telegram (DrKLO/Telegram, pinned commit `7be672ead283d022812d75c729d3a04dd4c2a2c6`) and runs `tg/apply.sh`.
- **`tg/apply.sh`:** copies `tg/overlay/` on top of Telegram, then makes edits with sed/python (name, package `com.rivers.rivpost.app`, API ID from secrets `TG_API_ID`/`TG_API_HASH`, Firebase file, ABIs, ccache).
- **`tg/overlay/`:** files added or replaced, mirroring Telegram's folder layout.
- **Output:** branch `apk-tg`, file `RivPost-TG.apk` (and `errors.txt` / `tail.txt` if the build failed).
- **Build:** about 2 hours (native engine). It uses 12 GB swap and `--max-workers=2` because the free runner ran out of memory (exit 143).
- **Old app:** Riv Post 2.2.5 (Compose + TDLib) on `main`. Code to reuse is in `app/src/main/java/com/rivers/rivpost/`.

## How each extra gets added
1. Put new code in `tg/overlay/TMessagesProj/src/main/java/com/rivers/rivpost/...`. Kotlin works if the Kotlin plugin is added to `TMessagesProj/build.gradle` from `apply.sh`; otherwise use Java.
2. Hook into Telegram's screens with small, targeted edits in `apply.sh` (python string replace that fails loudly if the anchor isn't found). Keep the hooks few and small, so updating Telegram later stays easy.
3. Settings: one new "Riv Post" section in Telegram's Settings (`SettingsActivity`) that opens our own settings screen. Each extra has its own switch, official behaviour is the default, and texts are in English and Arabic.
4. Data access: use Telegram's own classes (`MessagesController`, `MediaDataController`, `FileLoader`, `NotificationCenter`) instead of TDLib.

## Order (biggest first)
1. **Base check:** he installs `RivPost-TG.apk` and tests login, chats, calls, video, screen share.
2. **Riv Post settings entry**, plus a "Send debug log" row (logcat plus engine crash reports via `ApplicationExitInfo`).
3. **Music player:** port `music/` (Music, MusicLib, MusicService, Lyrics) and the music UI. Library sources come from Telegram's shared audio of the chosen chats. Connect Telegram's in-chat music bar (`FragmentContextView`) to our player.
4. **Video library and player:** port `media/` (Engine, Meta, Videos, VideoService) and the video UI. Add a "Play in Riv Post player" button to Telegram's `PhotoViewer`.
5. **Call extras:** add to Telegram's `VoIPService` / `VoIPFragment`. Audio hooks go in `org/webrtc/voiceengine/WebRtcAudioRecord/Track` (see `CallAudioHooks.java` on branch `video`). Then the screen share, video and group call extras.
6. **Privacy:** ghost mode, keep deleted messages, keep edit history, Vault, local pins.
7. **Customise:** extra look options; official stays the default.
8. **Chat, media, notification and small extras** from the list.
9. Retire the old app when nothing is missing.

## Rules
- Personal use, private repo. Name and icon are Riv Post; nothing inside is called "Telegram" after the full pass.
- Never type his API ID, hash, passwords or keys. They stay in GitHub secrets.
- Say "not tested on a real phone" with every delivery.
