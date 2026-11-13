#!/bin/bash
# Turns official Telegram's source (in $1) into Riv Post. Run from the repo root.
set -e
T="$1"
R="$(cd "$(dirname "$0")" && pwd)"

# 1. Our files on top of Telegram's
cp -r "$R/overlay/." "$T/"

# 2. Name, package, version
sed -i 's/^APP_PACKAGE=.*/APP_PACKAGE=com.rivers.rivpost.app/' "$T/gradle.properties"
# Version: keep Telegram's number, add our build number so APKs are tellable apart and updates install
if [ -n "$GITHUB_RUN_NUMBER" ]; then
  sed -i "s/^APP_VERSION_NAME=\(.*\)/APP_VERSION_NAME=\1 Riv $GITHUB_RUN_NUMBER/" "$T/gradle.properties"
fi
sed -i 's|<string name="AppName">Telegram</string>|<string name="AppName">Riv Post</string>|; s|<string name="AppNameBeta">Telegram Beta</string>|<string name="AppNameBeta">Riv Post Beta</string>|' "$T/TMessagesProj/src/main/res/values/strings.xml"

# 3. Your own API ID (GitHub secrets). Telegram requires apps built from its code to use their own.
if [ -n "$TG_API_ID" ] && [ -n "$TG_API_HASH" ]; then
  sed -i "s/public static int APP_ID = [0-9]*;/public static int APP_ID = $TG_API_ID;/; s/public static String APP_HASH = \"[0-9a-f]*\";/public static String APP_HASH = \"$TG_API_HASH\";/" "$T/TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java"
  echo "API ID set from secrets"
else
  echo "NO_API_ID" > "$T/../no-api-id"
fi

# 4. Firebase file matching our package (push stays off unless you set up your own)
python3 - "$T/TMessagesProj_App/google-services.json" <<'PY'
import json,sys
p=sys.argv[1]; j=json.load(open(p))
for c in j.get("client",[]):
    c["client_info"]["android_client_info"]["package_name"]="com.rivers.rivpost.app"
j["client"]=j["client"][:1]
json.dump(j,open(p,"w"),indent=2)
PY

# 5. Only real phones (arm64 and armv7), and a compile cache for the engine
sed -i 's/abiFilters "armeabi-v7a", "arm64-v8a", "x86", "x86_64"/abiFilters "armeabi-v7a", "arm64-v8a"/' "$T/TMessagesProj_App/build.gradle"
sed -i "s|arguments '-DANDROID_STL=c++_static', '-DANDROID_PLATFORM=android-23'|arguments '-DANDROID_STL=c++_static', '-DANDROID_PLATFORM=android-23', '-DCMAKE_C_COMPILER_LAUNCHER=ccache', '-DCMAKE_CXX_COMPILER_LAUNCHER=ccache'|" "$T/TMessagesProj/build.gradle"

# 6. Hooks: small anchored edits in Telegram's own files. Each fails the build loudly if its anchor moved.
python3 - "$T" <<'PY'
import sys
T = sys.argv[1]

def edit(path, old, new, count=1):
    p = T + '/' + path
    s = open(p).read()
    assert s.count(old) >= count, ('ANCHOR NOT FOUND', path, old[:90])
    s = s.replace(old, new, count)
    open(p, 'w').write(s)

SA = 'TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java'
# Riv Post row in Settings, after Language
edit(SA,
"""        items.add(SettingCell.Factory.of(10, IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom, R.drawable.settings_language, getString(R.string.SettingsLanguage), LocaleController.getCurrentLanguageName()));
""",
"""        items.add(SettingCell.Factory.of(10, IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom, R.drawable.settings_language, getString(R.string.SettingsLanguage), LocaleController.getCurrentLanguageName()));
        items.add(UItem.asShadow(null));
        items.add(SettingCell.Factory.of(100, 0xFF7C4DFF, 0xFF5E35B1, R.drawable.settings_features, getString(R.string.RivSettings), getString(R.string.RivSettingsInfo)));
""")
edit(SA,
"""            case 10:
                presentSettingFragment(new LanguageSelectActivity());
                break;
""",
"""            case 10:
                presentSettingFragment(new LanguageSelectActivity());
                break;
            case 100:
                presentSettingFragment(new RivSettingsActivity());
                break;
""")

MC = 'TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java'
# Hide typing
edit(MC,
"""    public boolean sendTyping(long dialogId, long threadMsgId, int action, String emojicon, int classGuid) {
""",
"""    public boolean sendTyping(long dialogId, long threadMsgId, int action, String emojicon, int classGuid) {
        if (RivPrefs.b(RivPrefs.HIDE_TYPING)) {
            return false;
        }
""")
# Hide online status: never send "I'm online"
edit(MC,
"""                    if (statusSettingState != 1 && (lastStatusUpdateTime == 0 || Math.abs(System.currentTimeMillis() - lastStatusUpdateTime) >= 55000 || offlineSent)) {
""",
"""                    if (!RivPrefs.b(RivPrefs.HIDE_ONLINE) && statusSettingState != 1 && (lastStatusUpdateTime == 0 || Math.abs(System.currentTimeMillis() - lastStatusUpdateTime) >= 55000 || offlineSent)) {
""")
# Ghost mode: never tell the server a chat was read
edit(MC,
"""    private void completeReadTask(ReadTask task) {
""",
"""    private void completeReadTask(ReadTask task) {
        if (RivPrefs.b(RivPrefs.GHOST)) {
            return;
        }
""")
# Keep deleted messages: ignore deletions coming from others
edit(MC,
"""                ArrayList<Integer> arrayList = deletedMessages.get(0);
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    deletedMessages.put(0, arrayList);
                }
                arrayList.addAll(update.messages);
""",
"""                ArrayList<Integer> arrayList = deletedMessages.get(0);
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    deletedMessages.put(0, arrayList);
                }
                if (!RivPrefs.b(RivPrefs.KEEP_DELETED)) {
                    arrayList.addAll(update.messages);
                }
""")
edit(MC,
"""                ArrayList<Integer> arrayList = deletedMessages.get(dialogId);
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    deletedMessages.put(dialogId, arrayList);
                }
                arrayList.addAll(update.messages);
""",
"""                ArrayList<Integer> arrayList = deletedMessages.get(dialogId);
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    deletedMessages.put(dialogId, arrayList);
                }
                if (!RivPrefs.b(RivPrefs.KEEP_DELETED)) {
                    arrayList.addAll(update.messages);
                }
""")
# Palette overrides (AMOLED / Green classic) each time the theme table is rebuilt
TH = 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/Theme.java'
edit(TH,
"""        shouldDrawGradientIcons = gradientIcons;
""",
"""        org.telegram.messenger.RivTheme.apply(colors, currentTheme.isDark());
        shouldDrawGradientIcons = gradientIcons;
""")

# Keep-alive notifications on by default
CM = 'TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java'
edit(CM,
"""            return MessagesController.getMainSettings(UserConfig.selectedAccount).getBoolean("backgroundConnection", false);
""",
"""            return MessagesController.getMainSettings(UserConfig.selectedAccount).getBoolean("backgroundConnection", org.telegram.messenger.RivPrefs.b(org.telegram.messenger.RivPrefs.KEEP_ALIVE, true));
""")
AL = 'TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java'
edit(AL,
"""            enabled = MessagesController.getMainSettings(UserConfig.selectedAccount).getBoolean("keepAliveService", false);
""",
"""            enabled = MessagesController.getMainSettings(UserConfig.selectedAccount).getBoolean("keepAliveService", RivPrefs.b(RivPrefs.KEEP_ALIVE, true));
""")

# Hide sponsored messages
edit(MC,
"""    public SponsoredMessagesInfo getSponsoredMessages(long dialogId) {
""",
"""    public SponsoredMessagesInfo getSponsoredMessages(long dialogId) {
        if (RivPrefs.b(RivPrefs.HIDE_SPONSORED)) {
            return null;
        }
""")

# Hide the stories row
DA = 'TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java'
edit(DA,
"""        dialogStoriesCellVisible = onlySelfStories || newVisibility;
""",
"""        dialogStoriesCellVisible = !org.telegram.messenger.RivPrefs.b(org.telegram.messenger.RivPrefs.HIDE_STORIES) && (onlySelfStories || newVisibility);
""")

# Sticker size
CC = 'TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java'
edit(CC,
"""                    } else {
                        maxHeight = maxWidth = (int) (Math.min(getParentWidth(), AndroidUtilities.displaySize.y) * 0.5f);
                    }
                    String filter;
""",
"""                    } else {
                        maxHeight = maxWidth = (int) (Math.min(getParentWidth(), AndroidUtilities.displaySize.y) * 0.5f);
                    }
                    float rivStickerScale = org.telegram.messenger.RivPrefs.stickerScale();
                    if (rivStickerScale != 1f) {
                        maxWidth = (int) (maxWidth * rivStickerScale);
                        maxHeight = maxHeight * rivStickerScale;
                    }
                    String filter;
""")

# Reminder alarm receiver in the manifest
MF = 'TMessagesProj/src/main/AndroidManifest.xml'
edit(MF,
"""    </application>
""",
"""        <receiver android:name="org.telegram.messenger.RivReminders$RivReminderReceiver" android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
    </application>
""")
# Vault: hidden chats disappear from every chat list
edit(DA,
"""    public ArrayList<TLRPC.Dialog> getDialogsArray(int currentAccount, int dialogsType, int folderId, boolean frozen) {
        if (frozen && frozenDialogsList != null) {
            return frozenDialogsList;
        }
""",
"""    public ArrayList<TLRPC.Dialog> getDialogsArray(int currentAccount, int dialogsType, int folderId, boolean frozen) {
        return org.telegram.messenger.RivVault.filterDialogs(getDialogsArrayInternal(currentAccount, dialogsType, folderId, frozen));
    }

    private ArrayList<TLRPC.Dialog> getDialogsArrayInternal(int currentAccount, int dialogsType, int folderId, boolean frozen) {
        if (frozen && frozenDialogsList != null) {
            return frozenDialogsList;
        }
""")
# Riv strings live in normal Android resources; teach the resolver to fall back to them
LC = 'TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java'
edit(LC,
"""        checkLocalizationInternal();
        value = localizationInternal.getByResNameOrResId(context, key, stringRes);
        if (value != null) {
            return value;
        }

        return localizationInternal.getByResName(fallback);
""",
"""        checkLocalizationInternal();
        value = localizationInternal.getByResNameOrResId(context, key, stringRes);
        if (value != null) {
            return value;
        }

        value = localizationInternal.getByResName(fallback);
        if (value == null && stringRes != 0 && context != null) {
            try {
                value = context.getString(stringRes);
            } catch (Exception ignored) {
            }
        }
        return value;
""")
print("Riv hooks applied")
PY

# ---------------------------------------------------------------------------
# TODO (deferred, not done in this pass -- see the agent's report for why):
#
# - Custom swipe actions per chat list (pick what left/right swipe does on a
#   chat row). This needs a hook into DialogsActivity's swipe/ItemTouchHelper
#   handling, which isn't in this overlay's sparse checkout of real Telegram
#   source and has no prior confirmed anchor in this file's edit() calls above.
#   Do not guess the anchor text blind -- fetch DialogsActivity.java for real,
#   locate its swipe callback, and extend it additively (read, don't replace).
#
# - Per-chat media auto-download override (disable auto-download for one
#   chat regardless of the global network-based setting). Needs a hook into
#   DownloadController's decision logic, another real file with no prior
#   anchor in this script. Same rule: confirm the exact anchor before editing.
#
# - Picture-in-picture for video messages/circles while scrolling. Needs a
#   hook into how round video messages play inline in the chat list (likely
#   ChatMessageCell / the round-video player). Lowest priority of the
#   deferred items; only attempt once the two above are confirmed-safe.
#
# - Draft indicator dot and per-folder unread badge: likely already covered
#   by stock Telegram's own dialog cell / folder tab rendering (draft text
#   preview and folder badge counts are already a stock feature) -- re-check
#   against a real build before adding anything, to avoid duplicating a
#   feature Telegram already has.
#
# - "Mark all read" on long-press of the Chats tab: needs to find how the
#   main tab bar / header currently handles long-press in LaunchActivity or
#   the bottom tab view, neither of which has a confirmed anchor here yet.
#
# - Theme colour-picker screen (swatches + "colour by current track" +
#   per-element colours) and the "Quick looks" layout picker (Side menu/tabs,
#   Floating, Classic bottom bar + Flat/Rounded style): both deferred by the
#   user's own request for this pass. No hook needed yet -- just not built.
#
# Everything else in this batch (quick replies, export chat, VIP contacts,
# the video/music library rebuild) is additive-only: new Riv*.java files plus
# extensions to our own RivSettingsActivity.java. No new real-file edits were
# needed for any of it.
# ---------------------------------------------------------------------------

echo "Riv Post changes applied"
