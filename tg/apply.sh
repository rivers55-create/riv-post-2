#!/bin/bash
# Turns official Telegram's source (in $1) into Riv Post. Run from the repo root.
set -e
T="$1"
R="$(cd "$(dirname "$0")" && pwd)"

# 1. Our files on top of Telegram's
cp -r "$R/overlay/." "$T/"

# 2. Name, package, version
sed -i 's/^APP_PACKAGE=.*/APP_PACKAGE=com.rivers.rivpost.app/' "$T/gradle.properties"
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
# Ghost mode: never tell the server a chat was read
edit(MC,
"""    private void completeReadTask(ReadTask task) {
""",
"""    private void completeReadTask(ReadTask task) {
        if (RivPrefs.b(RivPrefs.GHOST)) {
            return;
        }
""")
print("Riv hooks applied")
PY

echo "Riv Post changes applied"
