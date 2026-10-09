#!/usr/bin/env bash
# 一键发版：./scripts/release.sh 1.0.1
# 修改 versionName / versionCode → 提交 → 打 tag → 推送，
# 推送 tag 后 GitHub Actions（.github/workflows/release.yml）会自动构建并创建 Release。
set -euo pipefail

VERSION="${1:-}"
if [[ ! "$VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "用法: $0 <主.次.修订>，例如 $0 1.0.1" >&2
  exit 1
fi
MAJOR=${BASH_REMATCH[1]}; MINOR=${BASH_REMATCH[2]}; PATCH=${BASH_REMATCH[3]}
# 版本号单调递增：1.2.3 -> 10203
VERSION_CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH))
TAG="v$VERSION"

cd "$(git rev-parse --show-toplevel)"
GRADLE_FILE="app/build.gradle.kts"

[[ "$(git branch --show-current)" == "main" ]] || { echo "请先切换到 main 分支" >&2; exit 1; }
[[ -z "$(git status --porcelain)" ]] || { echo "工作区有未提交的改动，请先提交或清理" >&2; exit 1; }
git fetch -q origin main --tags
[[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || { echo "本地 main 与 origin/main 不一致，请先 pull/push" >&2; exit 1; }
! git rev-parse -q --verify "refs/tags/$TAG" >/dev/null || { echo "tag $TAG 已存在" >&2; exit 1; }

CURRENT_CODE=$(perl -ne 'print $1 if /versionCode\s*=\s*(\d+)/' "$GRADLE_FILE")
if (( VERSION_CODE <= CURRENT_CODE )); then
  echo "新的 versionCode($VERSION_CODE) 必须大于当前的 $CURRENT_CODE" >&2
  exit 1
fi

perl -pi -e "s/versionCode\s*=\s*\d+/versionCode = $VERSION_CODE/; s/versionName\s*=\s*\"[^\"]*\"/versionName = \"$VERSION\"/" "$GRADLE_FILE"

git add "$GRADLE_FILE"
git commit -m "chore: release $TAG"
git tag "$TAG"
git push origin main
git push origin "$TAG"

echo "已推送 $TAG，GitHub Actions 正在构建并创建 Release："
echo "  gh run watch   # 查看进度"
