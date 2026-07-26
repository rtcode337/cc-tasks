import type { Task } from '@/api/types'

// Web 版 Claude Code のプリフィル上限は約 5,000 文字。余裕を見て手前で切る
const PROMPT_LIMIT = 4500

/** GitHub のリポジトリ URL を owner/repo スラッグに変換する。GitHub 以外は null */
export function githubSlug(url: string): string | null {
  const m = url
    .trim()
    .match(/^(?:https?:\/\/(?:www\.)?|git@)github\.com[/:]([^/\s]+)\/([^/\s]+?)(?:\.git)?\/?$/i)
  return m ? `${m[1]}/${m[2]}` : null
}

/**
 * タスク内容をプリフィルした Claude Code の URL を組み立てる。
 * Claude モバイルアプリはクエリ(prompt / repositories)を引き継がないため、
 * 開く側(ClaudeCodeButton)が JS 遷移でユニバーサルリンクを回避し、
 * スマホでもブラウザ版が開くようにしている。
 * repositories は GitHub の URL だけを owner/repo に変換して渡す
 * (それ以外の URL は Claude 側が解釈できないため落とす)。
 */
export function claudeCodeUrl(task: Task, repoUrls?: string[]): string {
  const lines = [`cc-tasks のタスク #${task.id} に取り組んでください。`, '', task.title]
  if (task.context) lines.push('', '## コンテキスト', task.context)
  if (task.acceptanceCriteria) lines.push('', '## 受け入れ条件', task.acceptanceCriteria)
  if (task.outOfScope) lines.push('', '## スコープ外', task.outOfScope)
  lines.push(
    '',
    `作業の経緯や結果は、cc-tasks の MCP(add_note)が使えるならタスク #${task.id} に書き戻してください。` +
      'MCP が接続されていない場合は、代わりに最後へ経緯と結果の要約をまとめて出力してください(人手で書き戻します)。',
  )

  let prompt = lines.join('\n')
  if (prompt.length > PROMPT_LIMIT) {
    prompt = `${prompt.slice(0, PROMPT_LIMIT)}\n…(以下略。全文は cc-tasks のタスク #${task.id} を get_task で参照)`
  }

  const params = new URLSearchParams({ prompt })
  const slugs = (repoUrls ?? []).map(githubSlug).filter((s): s is string => s !== null)
  if (slugs.length > 0) params.set('repositories', slugs.join(','))
  return `https://claude.ai/code?${params.toString()}`
}
