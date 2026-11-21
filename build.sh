#!/data/data/com.termux/files/usr/bin/bash
set -u

cd "$HOME/bloatless" || exit 1

OUT_APK="bloatless-zion-aube.apk"
DOWNLOADS_APK="/sdcard/Download/$OUT_APK"
REPO_SLUG="zionaube512-sudo/bloatless"
REPO_URL="https://github.com/$REPO_SLUG"
RELEASE_TAG="${RELEASE_TAG:-v1.5-ZionAube}"
RELEASE_NOTES="Signed release of Bloatless by Zion Aube.

Apologies for having to change the signing key again — this is the second time it has happened, and this is the last time it is going to happen. I was making a rookie mistake, as I am a first-time app builder, and now the signing key is locked down permanently."

ANDROID_JAR="${ANDROID_JAR:-/data/data/com.termux/files/home/zion-obi/build/android.jar}"
if [ -f "$HOME/keystore.jks" ]; then
  KEYSTORE="${KEYSTORE:-$HOME/keystore.jks}"
elif [ -f "/sdcard/Bloatless_ZionAube_Backup/keystore.jks" ]; then
  KEYSTORE="${KEYSTORE:-/sdcard/Bloatless_ZionAube_Backup/keystore.jks}"
else
  KEYSTORE="${KEYSTORE:-$HOME/keystore.jks}"
fi
KEY_ALIAS="${KEY_ALIAS:-zionobi}"
STORE_PASS="${KEYSTORE_PASS:-zionobi}"
KEY_PASS="${KEY_PASS:-zionobi}"

rm -rf build/gen build/obj build/apk build/res.zip build/unsigned.apk build/aligned.apk build/sources.list build/classes.list bloatless-zion-obi.apk bloatless-zion-obi.apk.idsig "$OUT_APK" "${OUT_APK}.idsig" 2>/dev/null || true
mkdir -p build/gen build/obj build/apk || exit 1

aapt2 compile --dir res -o build/res.zip || { echo "[-] aapt2 compile failed"; exit 1; }
aapt2 link -o build/unsigned.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml build/res.zip --java build/gen || { echo "[-] aapt2 link failed"; exit 1; }

find src build/gen -name "*.java" > build/sources.list || exit 1
javac -cp "$ANDROID_JAR" -d build/obj @build/sources.list || { echo "[-] javac failed"; exit 1; }

find build/obj -type f -name "*.class" > build/classes.list || exit 1
if [ ! -s build/classes.list ]; then
  echo "[-] No .class files found in build/obj"
  exit 1
fi
d8 --lib "$ANDROID_JAR" --output build/apk @build/classes.list || { echo "[-] d8 failed"; exit 1; }

cp build/unsigned.apk build/aligned.apk || exit 1
(cd build/apk && zip -u ../aligned.apk classes*.dex) || { echo "[-] zip dex failed"; exit 1; }

if [ ! -f "$KEYSTORE" ]; then
  echo "[-] Keystore not found at: $KEYSTORE" >&2
  exit 1
fi

apksigner sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$STORE_PASS" \
  --key-pass "pass:$KEY_PASS" \
  --out "$OUT_APK" \
  build/aligned.apk || { echo "[-] apksigner failed"; exit 1; }

echo "BUILT & SIGNED: $(pwd)/$OUT_APK (keystore: $KEYSTORE, alias: $KEY_ALIAS)"

if mkdir -p /sdcard/Download 2>/dev/null && cp "$OUT_APK" "$DOWNLOADS_APK" 2>/dev/null; then
  echo "COPIED TO DOWNLOADS: $DOWNLOADS_APK"
else
  echo "[!] Could not copy to $DOWNLOADS_APK — run termux-setup-storage if needed."
fi

TOKEN="${GITHUB_TOKEN:-${GH_TOKEN:-}}"

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git remote set-url origin "$REPO_URL.git" 2>/dev/null || true
  git add -A
  if ! git diff --cached --quiet 2>/dev/null; then
    git commit -m "Release $RELEASE_TAG: Bloatless menu & signing updates" >/dev/null 2>&1 || true
  fi
  BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo main)"
  if [ -n "$TOKEN" ]; then
    ASKPASS_SCRIPT="$(mktemp)"
    printf '#!/bin/sh\ncase "$1" in *Username*) echo "zionaube512-sudo" ;; *) echo "%s" ;; esac\n' "$TOKEN" > "$ASKPASS_SCRIPT"
    chmod 700 "$ASKPASS_SCRIPT"
    GIT_ASKPASS="$ASKPASS_SCRIPT" GIT_TERMINAL_PROMPT=0 git push -u origin "$BRANCH"
    PUSH_RC=$?
    rm -f "$ASKPASS_SCRIPT"
    if [ "$PUSH_RC" -eq 0 ]; then
      echo "PUSHED TO GITHUB: $REPO_URL ($BRANCH)"
    else
      echo "[!] git push exited with status $PUSH_RC"
    fi
  else
    git push -u origin "$BRANCH" || true
  fi
fi

if [ -n "$TOKEN" ]; then
  PAYLOAD="$(RELEASE_TAG="$RELEASE_TAG" RELEASE_NOTES="$RELEASE_NOTES" python3 -c 'import json, os; print(json.dumps({"tag_name": os.environ["RELEASE_TAG"], "name": "Bloatless " + os.environ["RELEASE_TAG"], "body": os.environ["RELEASE_NOTES"], "draft": False, "prerelease": False}))')"
  REL_JSON="$(curl -sS -X POST \
    -H "Authorization: Bearer $TOKEN" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/$REPO_SLUG/releases" \
    -d "$PAYLOAD")"

  UPLOAD_URL="$(printf '%s' "$REL_JSON" | python3 -c 'import sys, json; d=json.load(sys.stdin); print(d.get("upload_url","").split("{")[0])' 2>/dev/null || true)"
  if [ -z "$UPLOAD_URL" ]; then
    EXISTING_JSON="$(curl -sS -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" "https://api.github.com/repos/$REPO_SLUG/releases/tags/$RELEASE_TAG")"
    REL_ID="$(printf '%s' "$EXISTING_JSON" | python3 -c 'import sys, json; d=json.load(sys.stdin); print(d.get("id",""))' 2>/dev/null || true)"
    UPLOAD_URL="$(printf '%s' "$EXISTING_JSON" | python3 -c 'import sys, json; d=json.load(sys.stdin); print(d.get("upload_url","").split("{")[0])' 2>/dev/null || true)"
    if [ -n "$REL_ID" ]; then
      curl -sS -X PATCH \
        -H "Authorization: Bearer $TOKEN" \
        -H "Accept: application/vnd.github+json" \
        "https://api.github.com/repos/$REPO_SLUG/releases/$REL_ID" \
        -d "$(RELEASE_NOTES="$RELEASE_NOTES" python3 -c 'import json, os; print(json.dumps({"body": os.environ["RELEASE_NOTES"]}))')" >/dev/null
    fi
  fi

  if [ -n "$UPLOAD_URL" ]; then
    ASSET_RES="$(curl -sS -X POST \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/vnd.android.package-archive" \
      --data-binary @"$OUT_APK" \
      "${UPLOAD_URL}?name=${OUT_APK}")"
    ASSET_ERR="$(printf '%s' "$ASSET_RES" | python3 -c 'import sys, json; d=json.load(sys.stdin); print(d.get("message",""))' 2>/dev/null || true)"
    if [ -z "$ASSET_ERR" ]; then
      echo "GITHUB RELEASE PUBLISHED: $REPO_URL/releases/tag/$RELEASE_TAG"
    else
      echo "[!] Asset upload note: $ASSET_ERR"
    fi
  else
    echo "[-] Could not create or find release $RELEASE_TAG: $REL_JSON"
  fi
else
  echo "[-] GITHUB_TOKEN not set"
fi
