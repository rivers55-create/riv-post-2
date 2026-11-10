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
echo "Riv Post changes applied"
