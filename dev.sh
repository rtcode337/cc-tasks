#!/usr/bin/env bash
#
# ローカル開発用のホットリロード環境をまとめて起動する。
#
#   backend  : ./gradlew bootRun (dev プロファイル) + 継続コンパイル
#              → dev は認証を通さないのでログイン不要。
#              → ソースを保存して再コンパイルされると devtools が数秒で自動再起動。
#   frontend : Vite dev server (HMR)。/api は backend にプロキシ。
#
# 開くのは frontend 側: http://localhost:7000
# スマホなどからドメイン名で開くときは VITE_ALLOWED_HOSTS を付ける
# (Vite は localhost と IP 直打ち以外を既定で 403 にするため):
#   VITE_ALLOWED_HOSTS=dev.example.lan ./dev.sh
# Ctrl-C で全部まとめて停止する。
#
# ※ Docker を毎回リビルドする必要はない。これで反復するのが速い。
#   本番同等(Google ログインあり)で確認したいときだけ docker compose を使う。

set -uo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"

# java が PATH に無く JAVA_HOME も未設定なら、よくある場所から JDK 25 を探す
# (build.gradle.kts の toolchain が 25。ここを変えるときは両方揃える)
if [ -z "${JAVA_HOME:-}" ] && ! command -v java >/dev/null 2>&1; then
  for d in /usr/lib/jvm/java-25-* /usr/lib/jvm/temurin-25-* /opt/java/openjdk; do
    [ -x "$d/bin/java" ] && export JAVA_HOME="$d" && break
  done
fi
if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! command -v java >/dev/null 2>&1; then
  echo "JDK 25 が見つかりません。JAVA_HOME を設定してから実行してください。" >&2
  exit 1
fi

pids=()
cleanup() {
  trap - EXIT INT TERM
  echo
  echo "停止中…"
  kill "${pids[@]}" 2>/dev/null
  wait 2>/dev/null
}
trap cleanup EXIT INT TERM

echo "▶ backend  : http://localhost:7001 (dev / 認証なし / 自動再起動)"
echo "▶ frontend : http://localhost:7000 (HMR) ← ブラウザで開くのはこちら"
echo "  Ctrl-C で全部停止"
echo

# 1) バックエンド本体(dev プロファイル)
( cd "$ROOT/backend" && ./gradlew bootRun --args='--spring.profiles.active=dev' --console=plain ) &
pids+=($!)

# 2) 継続コンパイル。保存 → classes 再生成 → devtools が再起動を検知
( cd "$ROOT/backend" && ./gradlew -t classes --console=plain -q ) &
pids+=($!)

# 3) フロント(HMR)
( cd "$ROOT/frontend" && npm run dev ) &
pids+=($!)

wait
