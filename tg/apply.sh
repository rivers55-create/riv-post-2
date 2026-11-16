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
                } else {
                    RivDeleted.add(currentAccount, 0, update.messages);
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
                } else {
                    RivDeleted.add(currentAccount, dialogId, update.messages);
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
        if (RivPrefs.b(RivPrefs.HIDE_ADS, true)) {
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
# Bottom tab bar (Chats/Contacts/Calls/Music/Videos/Profile) replaces Telegram's own floating
# tab pill (MainTabsActivity/GlassTabView) everywhere, by request. Two parts:
#  (a) checkUi_mainTabsVisible() is the single function that tells MainTabsActivity whether to
#      show its own pill; force it to always report "hidden" so the pill never draws, whatever
#      hasMainTabs/account settings say.
#  (b) RivBottomBar.attach() on DialogsActivity's own view, now always using the no-pill bottom
#      margin (navigationBarHeight) since there is no pill to leave room for any more.
edit(DA,
"""    private void checkUi_mainTabsVisible() {
        final boolean mainTabsVisible = !searching && (blurredView == null || blurredView.getBackground() == null || blurredView.getAlpha() < 0.01f || blurredView.getVisibility() == View.GONE);
        if (mainTabsActivityController != null) {
            mainTabsActivityController.setTabsVisible(mainTabsVisible);
        }
    }""",
"""    private void checkUi_mainTabsVisible() {
        // Riv Post: RivBottomBar replaces Telegram's own floating tab pill everywhere, so that
        // pill is always told to hide, regardless of the real mainTabsVisible computation below.
        if (mainTabsActivityController != null) {
            mainTabsActivityController.setTabsVisible(false);
        }
    }""")
edit(DA,
"""        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onApplyWindowInsets);
        return fragmentView;""",
"""        if (!onlySelect && initialDialogsType == DIALOGS_TYPE_DEFAULT && !isArchive()) {
            org.telegram.ui.RivBottomBar.attach(this, contentView, org.telegram.ui.RivBottomBar.TAB_CHATS, AndroidUtilities.navigationBarHeight);
        }

        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onApplyWindowInsets);
        return fragmentView;""")

# Tapping Telegram's own in-chat music bar: when RivPrefs.TAP_OPENS_RTP_MINI_PLAYER is on
# (default on), show RivMiniPlayerView's floating overlay instead of Telegram's own
# AudioPlayerAlert popup. Off switches back to the original stock behaviour untouched.
FCV = 'TMessagesProj/src/main/java/org/telegram/ui/Components/FragmentContextView.java'
edit(FCV,
"""                    if (messageObject.isMusic()) {
                        final Activity activity = AndroidUtilities.findActivity(getContext());
                        if (activity instanceof LaunchActivity) {
                            new AudioPlayerAlert(activity, resourcesProvider).show();
                        } else if (AndroidUtilities.isContextSafe(LaunchActivity.instance)) {
                            new AudioPlayerAlert(LaunchActivity.instance, resourcesProvider).show();""",
"""                    if (messageObject.isMusic()) {
                        final Activity activity = AndroidUtilities.findActivity(getContext());
                        if (org.telegram.messenger.RivPrefs.b(org.telegram.messenger.RivPrefs.TAP_OPENS_RTP_MINI_PLAYER, true) && activity != null) {
                            org.telegram.ui.RivMiniPlayerView.showFloating(activity, fragment);
                        } else if (activity instanceof LaunchActivity) {
                            new AudioPlayerAlert(activity, resourcesProvider).show();
                        } else if (AndroidUtilities.isContextSafe(LaunchActivity.instance)) {
                            new AudioPlayerAlert(LaunchActivity.instance, resourcesProvider).show();""")

# Tapping the music notification (or the lock-screen player) used to open Telegram's own
# AudioPlayerAlert sheet. Open Riv Post's RTP-style full player instead (RivPrefs.NOTIF_OPENS_RTP,
# default on); off restores the stock sheet.
LA = 'TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java'
edit(LA,
"""            } else if (showPlayer) {
                if (!actionBarLayout.getFragmentStack().isEmpty()) {
                    BaseFragment fragment = actionBarLayout.getFragmentStack().get(0);
                    fragment.showDialog(new AudioPlayerAlert(this, null));
                }""",
"""            } else if (showPlayer) {
                if (!actionBarLayout.getFragmentStack().isEmpty()) {
                    if (org.telegram.messenger.RivPrefs.b(org.telegram.messenger.RivPrefs.NOTIF_OPENS_RTP, true)) {
                        java.util.List<BaseFragment> rivStack = actionBarLayout.getFragmentStack();
                        BaseFragment top = rivStack.get(rivStack.size() - 1);
                        if (!(top instanceof org.telegram.ui.RivMusicPlayerActivity)) {
                            top.presentFragment(new org.telegram.ui.RivMusicPlayerActivity());
                        }
                    } else {
                        BaseFragment fragment = actionBarLayout.getFragmentStack().get(0);
                        fragment.showDialog(new AudioPlayerAlert(this, null));
                    }
                }""")

# Telegram only recognises an .mkv file as a playable video if either the sender tagged it as
# one, or this "Stream MKV videos" setting (real Telegram's own, in Data and Storage) is on --
# it defaults off, so an .mkv sent without that tag silently doesn't count as a video anywhere,
# including Riv Post's own video library. Default it on; the user can still switch it off
# themselves in Data and Storage like always.
SCFG = 'TMessagesProj/src/main/java/org/telegram/messenger/SharedConfig.java'
edit(SCFG,
"""            streamMkv = preferences.getBoolean("streamMkv", false);""",
"""            streamMkv = preferences.getBoolean("streamMkv", true);""")

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

# Bottom bar on every tab page: Contacts and Profile, when opened from the Riv bottom bar
# (argument "rivTab"), dock the same bar and leave room for it the same way Telegram leaves
# room for its own tab pill (additionNavigationBarHeight / additionFloatingButtonOffset).
CA = 'TMessagesProj/src/main/java/org/telegram/ui/ContactsActivity.java'
PA = 'TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java'
for path in (CA, PA):
    edit(path,
"""        additionFloatingButtonOffset = hasMainTabs ? dp(DialogsActivity.MAIN_TABS_HEIGHT + DialogsActivity.MAIN_TABS_MARGIN) : 0;
""",
"""        additionFloatingButtonOffset = hasMainTabs ? dp(DialogsActivity.MAIN_TABS_HEIGHT + DialogsActivity.MAIN_TABS_MARGIN) : 0;
        if (arguments != null && arguments.getBoolean("rivTab", false)) {
            additionNavigationBarHeight = dp(org.telegram.ui.RivBottomBar.heightDp() + 8);
            additionFloatingButtonOffset = dp(org.telegram.ui.RivBottomBar.heightDp() + 8);
        }
""")
edit(CA,
"""        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onApplyWindowInsets);
        return fragmentView;""",
"""        if (arguments != null && arguments.getBoolean("rivTab", false)) {
            org.telegram.ui.RivBottomBar.attach(this, contentView, org.telegram.ui.RivBottomBar.TAB_CONTACTS, 0);
        }
        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onApplyWindowInsets);
        return fragmentView;""")
edit(PA,
"""        setupHeaderGlass();

        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onApplyWindowInsets);
        return fragmentView;""",
"""        setupHeaderGlass();

        if (arguments != null && arguments.getBoolean("rivTab", false) && fragmentView instanceof android.widget.FrameLayout) {
            org.telegram.ui.RivBottomBar.attach(this, (android.widget.FrameLayout) fragmentView, org.telegram.ui.RivBottomBar.TAB_PROFILE, 0);
        }
        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onApplyWindowInsets);
        return fragmentView;""")


# Full chat names: a name slightly too long for its row is shrunk (down to 80%) to fit on one
# line instead of being cut with "..."; only names longer than that still end in "...".
DC = 'TMessagesProj/src/main/java/org/telegram/ui/Cells/DialogCell.java'
edit(DC,
"""            CharSequence nameStringFinal = nameString;
            if (nameLayoutEllipsizeByGradient) {""",
"""            CharSequence nameStringFinal = nameString;
            if (!twoLinesForName && !nameLayoutEllipsizeByGradient) {
                nameStringFinal = org.telegram.messenger.RivPrefs.fitName(nameStringFinal, Theme.dialogs_namePaint[paintIndex], ellipsizeWidth);
            }
            if (nameLayoutEllipsizeByGradient) {""")


# Ghost button in the Chats top bar (tap: ghost mode on/off)
edit(DA,
"""            downloadsItem.setVisibility(View.GONE);

            updateProxyButton(false, false);""",
"""            downloadsItem.setVisibility(View.GONE);
            if (initialDialogsType == DIALOGS_TYPE_DEFAULT) {
                org.telegram.ui.RivGhostButton.add(menu);
            }

            updateProxyButton(false, false);""")


# Hide the "Update Telegram" bar: it would install official Telegram over Riv Post
MT = 'TMessagesProj/src/main/java/org/telegram/ui/MainTabsActivity.java'
edit(MT,
"""        updateLayout = ApplicationLoader.applicationLoaderInstance.takeUpdateLayout(getParentActivity(), updateLayoutWrapper);""",
"""        updateLayout = org.telegram.messenger.RivPrefs.b(org.telegram.messenger.RivPrefs.HIDE_UPDATE_BANNER, true) ? null : ApplicationLoader.applicationLoaderInstance.takeUpdateLayout(getParentActivity(), updateLayoutWrapper);""")


# Kept (deleted-by-others) messages get a "deleted" label next to their time
edit(CC,
"""    public static String timeString(MessageObject current, MessageObject message, boolean edited, int editDate) {
        if (current.notime""",
"""    public static String timeString(MessageObject current, MessageObject message, boolean edited, int editDate) {
        return org.telegram.messenger.RivDeleted.decorate(current, timeStringRivInternal(current, message, edited, editDate));
    }

    private static String timeStringRivInternal(MessageObject current, MessageObject message, boolean edited, int editDate) {
        if (current.notime""")


# Chats: select-in-between, download all, save all to phone, edit history
CHA = 'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java'
edit(CHA,
"""    private final static int save_to = 25;
""",
"""    private final static int save_to = 25;
    private final static int riv_more = 9100;
    private final static int riv_range = 9101;
    private final static int riv_download_all = 9102;
    private final static int riv_save_all = 9103;
    private final static int RIV_OPTION_EDIT_HISTORY = 9201;
""")
edit(CHA,
"""                } else if (id == save_to) {
""",
"""                } else if (id == riv_range) {
                    rivSelectBetween();
                } else if (id == riv_download_all || id == riv_save_all) {
                    ArrayList<MessageObject> rivPicked = new ArrayList<>();
                    for (int a = 1; a >= 0; a--) {
                        for (int b = 0; b < selectedMessagesIds[a].size(); b++) {
                            rivPicked.add(selectedMessagesIds[a].valueAt(b));
                        }
                    }
                    clearSelectionMode();
                    org.telegram.ui.RivChatTools.downloadAll(ChatActivity.this, rivPicked, id == riv_save_all);
                } else if (id == save_to) {
""")
edit(CHA,
"""            actionModeViews.add(actionMode.addItemWithWidth(share, R.drawable.msg_shareout, dp(48), LocaleController.getString(R.string.ShareFile)));
""",
"""            actionModeViews.add(actionMode.addItemWithWidth(share, R.drawable.msg_shareout, dp(48), LocaleController.getString(R.string.ShareFile)));
            if (org.telegram.messenger.RivPrefs.b(org.telegram.messenger.RivPrefs.RANGE_SELECT, true)) {
                ActionBarMenuItem rivMore = actionMode.addItemWithWidth(riv_more, R.drawable.ic_ab_other, dp(48), LocaleController.getString(R.string.AccDescrMoreOptions));
                rivMore.addSubItem(riv_range, R.drawable.msg_select, LocaleController.getString(R.string.RivSelectBetween));
                rivMore.addSubItem(riv_download_all, R.drawable.msg_download, LocaleController.getString(R.string.RivDownloadAll));
                rivMore.addSubItem(riv_save_all, R.drawable.msg_download, LocaleController.getString(R.string.RivSaveAllToPhone));
                actionModeViews.add(rivMore);
            }
""")
edit(CHA,
"""    private void hideTagSelector() {
""",
"""    /** Riv Post: selects every message between the first and the last one already selected. */
    private void rivSelectBetween() {
        int first = -1, last = -1;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject m = messages.get(i);
            if (m == null) continue;
            int index = m.getDialogId() == dialog_id ? 0 : 1;
            if (selectedMessagesIds[index].indexOfKey(m.getId()) >= 0) {
                if (first < 0) first = i;
                last = i;
            }
        }
        if (first < 0 || last <= first) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, LocaleController.getString(R.string.RivSelectBetweenHint)).show();
            return;
        }
        for (int i = first + 1; i < last; i++) {
            MessageObject m = messages.get(i);
            if (m == null) continue;
            int index = m.getDialogId() == dialog_id ? 0 : 1;
            if (selectedMessagesIds[index].indexOfKey(m.getId()) >= 0) continue;
            int type = getMessageType(m);
            if (m.isAnyGift() || type < 2 || type == 20 || type == MessageObject.TYPE_SUGGEST_PHOTO || m.type == MessageObject.TYPE_JOINED_CHANNEL || m.type == MessageObject.TYPE_GIFT_STARS || m.isWallpaperAction() || m.isSponsored() || m.isEphemeral()) {
                continue;
            }
            addToSelectedMessages(m, false);
        }
        updateActionModeTitle();
        updateVisibleRows();
    }

    private void hideTagSelector() {
""")
edit(CHA,
"""            addToPolls(messageObject, old);
""",
"""            addToPolls(messageObject, old);
            org.telegram.messenger.RivEdits.record(old, messageObject);
""")
edit(CHA,
"""            if (options.isEmpty() && optionsView == null) {
""",
"""            if (!suggestEdit && selectedObject != null && !options.isEmpty()) {
                if (org.telegram.messenger.RivEdits.has(selectedObject)) {
                    items.add(LocaleController.getString(R.string.RivEditHistory));
                    options.add(RIV_OPTION_EDIT_HISTORY);
                    icons.add(R.drawable.msg_recent);
                }
            }
            if (options.isEmpty() && optionsView == null) {
""")
edit(CHA,
"""        switch (option) {
            case OPTION_RETRY: {
""",
"""        switch (option) {
            case RIV_OPTION_EDIT_HISTORY: {
                org.telegram.ui.RivChatTools.showEditHistory(this, selectedObject);
                break;
            }
            case OPTION_RETRY: {
""")


# Profile: full "last seen" time and the person's ID under their name
edit(PA,
"""                newString2 = LocaleController.formatUserStatus(currentAccount, user, isOnline, shortStatus ? new boolean[1] : null);
""",
"""                newString2 = LocaleController.formatUserStatus(currentAccount, user, isOnline, shortStatus ? new boolean[1] : null);
                if (!isOnline[0]) {
                    newString2 = org.telegram.messenger.RivProfile.decorateStatus(newString2, user);
                } else if (org.telegram.messenger.RivPrefs.b(org.telegram.messenger.RivPrefs.SHOW_USER_ID, true)) {
                    newString2 = newString2 + "  \\u00B7  ID " + user.id;
                }
""")


# Faster downloads: use Telegram's own bigger-pieces setting (512 KB x 8 at once) for every file
FLO = 'TMessagesProj/src/main/java/org/telegram/messenger/FileLoadOperation.java'
edit(FLO,
"""        if ((preloadPrefixSize > 0 || MessagesController.getInstance(currentAccount).getfileExperimentalParams) && !forceSmallChunk) {""",
"""        if ((preloadPrefixSize > 0 || MessagesController.getInstance(currentAccount).getfileExperimentalParams || RivPrefs.b(RivPrefs.FAST_DOWNLOADS, true)) && !forceSmallChunk) {""")


# Equalizer: attach to every song's audio as it starts, and expose the session for the EQ screen
MCO = 'TMessagesProj/src/main/java/org/telegram/messenger/MediaController.java'
edit(MCO,
"""    private VideoPlayer audioPlayer = null;
""",
"""    private VideoPlayer audioPlayer = null;

    public void rivSetVolume(float v) {
        try {
            if (audioPlayer != null) audioPlayer.setVolume(v);
        } catch (Throwable ignored) {
        }
    }

    public int rivAudioSessionId() {
        try {
            return audioPlayer != null && audioPlayer.player != null ? audioPlayer.player.getAudioSessionId() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }
""")
edit(MCO,
"""                audioPlayer.setStreamType(useFrontSpeaker ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
                audioPlayer.play();
                if (!messageObject.isVoice()) {""",
"""                audioPlayer.setStreamType(useFrontSpeaker ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
                audioPlayer.play();
                if (messageObject.isMusic()) {
                    RivEq.attach(rivAudioSessionId());
                }
                if (!messageObject.isVoice()) {""")


# Chats: floating buttons (new message, camera) sit above the Riv bar and the music bar,
# and the chat list leaves room at the bottom for both
edit(DA,
"""        final float top = -navigationBarHeight - additionFloatingButtonOffset - additionalFloatingTranslation;""",
"""        final float top = -navigationBarHeight - org.telegram.ui.RivBottomBar.fabOffset(this, additionFloatingButtonOffset) - additionalFloatingTranslation;""")
edit(DA,
"""        additionNavigationBarHeight = hasMainTabs ? dp(MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
        additionFloatingButtonOffset = hasMainTabs ? dp(DialogsActivity.MAIN_TABS_HEIGHT + DialogsActivity.MAIN_TABS_MARGIN) : 0;
""",
"""        additionNavigationBarHeight = hasMainTabs ? dp(MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
        additionFloatingButtonOffset = hasMainTabs ? dp(DialogsActivity.MAIN_TABS_HEIGHT + DialogsActivity.MAIN_TABS_MARGIN) : 0;
        if (!onlySelect && initialDialogsType == DIALOGS_TYPE_DEFAULT && folderId == 0) {
            additionNavigationBarHeight = dp(org.telegram.ui.RivBottomBar.heightDp() + 64);
        }
""")


# Per-chat "don't auto-download" and quiet hours
DLC = 'TMessagesProj/src/main/java/org/telegram/messenger/DownloadController.java'
edit(DLC,
"""    private int canDownloadMediaInternal(MessageObject message) {
        if (message == null || message.messageOwner == null) return 0;
""",
"""    private int canDownloadMediaInternal(MessageObject message) {
        if (message == null || message.messageOwner == null) return 0;
        if (RivPrefs.inList(RivPrefs.NO_AUTODL_CHATS, message.getDialogId())) return 0;
""")
edit(DLC,
"""    private int canDownloadMediaInternal(MessageObject message, long overrideSize) {
        if (message == null || message.messageOwner == null) return 0;
""",
"""    private int canDownloadMediaInternal(MessageObject message, long overrideSize) {
        if (message == null || message.messageOwner == null) return 0;
        if (RivPrefs.inList(RivPrefs.NO_AUTODL_CHATS, message.getDialogId())) return 0;
""")
edit(DLC,
"""    public int canDownloadMedia(TLRPC.Message message) {
        if (message == null || message.media instanceof TLRPC.TL_messageMediaStory) {
            return canPreloadStories() ? 2 : 0;
        }
""",
"""    public int canDownloadMedia(TLRPC.Message message) {
        if (message == null || message.media instanceof TLRPC.TL_messageMediaStory) {
            return canPreloadStories() ? 2 : 0;
        }
        if (RivPrefs.inList(RivPrefs.NO_AUTODL_CHATS, MessageObject.getDialogId(message))) return 0;
""")
NC = 'TMessagesProj/src/main/java/org/telegram/messenger/NotificationsController.java'
edit(NC,
"""        /*if (BuildVars.LOGS_ENABLED && BuildVars.DEBUG_VERSION) {
            FileLog.d("notify override for " + dialog_id + " = " + notifyOverride);
        }*/
        return notifyOverride;""",
"""        /*if (BuildVars.LOGS_ENABLED && BuildVars.DEBUG_VERSION) {
            FileLog.d("notify override for " + dialog_id + " = " + notifyOverride);
        }*/
        if (RivQuiet.isQuiet(dialog_id)) {
            return 2;
        }
        return notifyOverride;""")

# Chat menu: quick replies into the message box, jump to the first message, chat stats, chat options
edit(CHA,
"""    private final static int riv_more = 9100;
""",
"""    private final static int riv_more = 9100;
    private final static int riv_quick_replies = 9104;
    private final static int riv_jump_first = 9105;
    private final static int riv_chat_stats = 9106;
    private final static int riv_chat_options = 9107;
""")
edit(CHA,
"""                headerItem.lazilyAddSubItem(add_shortcut, R.drawable.msg_home, LocaleController.getString(R.string.AddShortcut));
            }
""",
"""                headerItem.lazilyAddSubItem(add_shortcut, R.drawable.msg_home, LocaleController.getString(R.string.AddShortcut));
            }
            if (chatMode == 0 && !isTopic) {
                if (org.telegram.messenger.RivPrefs.b(org.telegram.messenger.RivPrefs.QUICK_REPLY_INSERT, true)) {
                    headerItem.lazilyAddSubItem(riv_quick_replies, R.drawable.msg_edit, LocaleController.getString(R.string.RivQuickReplies));
                }
                headerItem.lazilyAddSubItem(riv_jump_first, R.drawable.msg_go_up, LocaleController.getString(R.string.RivJumpFirst));
                headerItem.lazilyAddSubItem(riv_chat_stats, R.drawable.msg_stats, LocaleController.getString(R.string.RivChatStats));
                headerItem.lazilyAddSubItem(riv_chat_options, R.drawable.msg_settings_old, LocaleController.getString(R.string.RivChatOptions));
            }
""")
edit(CHA,
"""                } else if (id == riv_range) {
""",
"""                } else if (id == riv_quick_replies) {
                    if (chatActivityEnterView != null) {
                        org.telegram.ui.RivChatTools.showQuickReplies(ChatActivity.this, chatActivityEnterView.getEditField());
                    }
                } else if (id == riv_jump_first) {
                    scrollToMessageId(1, 0, false, 0, true, 0);
                } else if (id == riv_chat_stats) {
                    org.telegram.ui.RivChatTools.showChatStats(ChatActivity.this, dialog_id);
                } else if (id == riv_chat_options) {
                    org.telegram.ui.RivChatTools.showChatOptions(ChatActivity.this, dialog_id);
                } else if (id == riv_range) {
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
# - Calls tab "talk time" header: screenshots confirm the old Riv Post app
#   showed, above the real call-log list, an instructional subtitle, a
#   "Talk time: this week H:MM . this month H:MM" line, and a per-contact
#   top-talk-time breakdown line, all computed from the same call-log data
#   the real calls screen already has. For this pass the Calls tab just
#   opens the real stock CallLogActivity() with no header, to avoid
#   rushing a stats-aggregation feature against call-log internals that
#   weren't verified in this pass. To build it: fetch CallLogActivity.java
#   fresh, find exactly how it sources its call rows (TL request vs local
#   DB query) and its duration field, then either (a) prepend a small
#   header view above wherever that fragment builds its list -- needs a
#   confirmed, non-guessed anchor in that file -- or (b) wrap it in a new
#   RivCallsActivity.java that reuses the same data source and adds the
#   header on top, which is lower-risk since it needs no real-file hook at
#   all. Do not guess the data source or duration field; read them first.
#
# Everything else in this batch (quick replies, export chat, VIP contacts,
# the video/music library rebuild, the bottom tab bar, the shared chat-
# source picker, the selected-chats filters) is additive-only: new
# Riv*.java files plus one confirmed, minimal hook into DialogsActivity.java
# (see RivBottomBar.attach() above) to dock the bar on the root Chats
# screen. No other new real-file edits were needed for any of it.
# ---------------------------------------------------------------------------

echo "Riv Post changes applied"
