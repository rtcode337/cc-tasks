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
 * スマホでの開き方(ユニバーサルリンク対策・PWA の x-safari- スキーム)は
 * ClaudeCodeButton 側で吸収する。ここは素の https URL を返す。
 * repositories は GitHub の URL だけを owner/repo に変換して渡す
 * (それ以外の URL は Claude 側が解釈できないため落とす)。
 */
export function claudeCodeUrl(task: Task, repoUrls?: string[]): string {
  const lines = [`cc-tasks のタスク #${task.id} に取り組んでください。`, '', task.title]
  let prompt = lines.join('\n')
  if (prompt.length > PROMPT_LIMIT) {
    prompt = `${prompt.slice(0, PROMPT_LIMIT)}\n…(以下略。全文は cc-tasks のタスク #${task.id} を参照)`
  }

  const params = new URLSearchParams({ prompt })
  const slugs = (repoUrls ?? []).map(githubSlug).filter((s): s is string => s !== null)
  if (slugs.length > 0) params.set('repositories', slugs.join(','))
  return `https://claude.ai/code?${params.toString()}`
}
