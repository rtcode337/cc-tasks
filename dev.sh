#!/usr/bin/env bash
#
# ローカル開発用のホットリロード環境をまとめて起動する。
#
#   backend  : ./gradlew bootRun (dev プロファイル) + 継続コンパイル
#              → dev は認証を通さないのでログイン不要。
#              → ソースを保存して再コンパイルされると devtools が数秒で自動再起動。
#   frontend : Vite dev server (HMR)。/api と /mcp は backend にプロキシ。
#
# 開くのは frontend 側: http://localhost:8931
# Ctrl-C で全部まとめて停止する。
#
# ※ Docker を毎回リビルドする必要はない。これで反復するのが速い。
#   本番同等(Google ログインあり)で確認したいときだけ docker compose を使う。

set -uo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"

# java が PATH に無く JAVA_HOME も未設定なら、よくある場所から JDK 21 を探す
if [ -z "${JAVA_HOME:-}" ] && ! command -v java >/dev/null 2>&1; then
  for d in /usr/lib/jvm/java-21-* /usr/lib/jvm/temurin-21-* /opt/java/openjdk; do
    [ -x "$d/bin/java" ] && export JAVA_HOME="$d" && break
  done
fi
if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! command -v java >/dev/null 2>&1; then
  echo "JDK 21 が見つかりません。JAVA_HOME を設定してから実行してください。" >&2
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

echo "▶ backend  : http://localhost:7000 (dev / 認証なし / 自動再起動)"
echo "▶ frontend : http://localhost:8931 (HMR) ← ブラウザで開くのはこちら"
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
