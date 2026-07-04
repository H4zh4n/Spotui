#!/usr/bin/env bash
# Commit everything, push, and publish the APK to the rolling "Release" tag.
#
# Usage: ./release.sh [commit message]
#
# The in-app update checker reads the release TITLE ("spotui <version>") and
# compares it against the installed versionName — bump versionName in
# app/build.gradle.kts before releasing or users won't be prompted.
set -euo pipefail
cd "$(dirname "$0")"

# Repo the release is published to. Must match UpdateChecker.RELEASE_API
# (app/src/main/java/com/music/spotui/data/update/UpdateChecker.kt).
REPO="Spotui/Spotui"
TAG="Release"

MSG="${1:-Update spotui}"

VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
[ -n "$VERSION" ] || { echo "Could not read versionName from app/build.gradle.kts" >&2; exit 1; }

echo "==> Building spotui $VERSION"
./gradlew :app:assembleRelease

APK_SRC="app/build/outputs/apk/release/app-release.apk"
# Staged outside the repo so `git add -A` below can't pick it up.
APK="$(mktemp -d)/spotui-$VERSION.apk"
[ -f "$APK_SRC" ] || { echo "APK not found at $APK_SRC" >&2; exit 1; }
cp "$APK_SRC" "$APK"

echo "==> Committing and pushing"
git add -A
git diff --cached --quiet || git commit -m "$MSG"
git push origin HEAD

echo "==> Publishing release to $REPO ($TAG)"
# Rolling release: replace the previous one so the tag always points at the
# latest build. --cleanup-tag also drops the old git tag.
gh release delete "$TAG" -R "$REPO" --cleanup-tag -y 2>/dev/null || true
gh release create "$TAG" "$APK" \
    -R "$REPO" \
    --title "spotui $VERSION" \
    --notes "$MSG"

rm -f "$APK"
echo "==> Done: spotui $VERSION published"
