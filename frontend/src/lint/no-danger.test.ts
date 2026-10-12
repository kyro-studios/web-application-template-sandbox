// @vitest-environment node
import { ESLint } from 'eslint'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

// frontend/ の設定 (eslint.config.js) で、src/ 以下のファイルを検査する。ESLint は Node の環境で動かす
const frontendDir = fileURLToPath(new URL('../../', import.meta.url))
const sampleFile = fileURLToPath(new URL('../Sample.tsx', import.meta.url))

describe('ESLint の設定', () => {
  it('dangerouslySetInnerHTML を使うと、react/no-danger のエラーになる', async () => {
    const eslint = new ESLint({ cwd: frontendDir })
    const source = [
      'export function Unsafe({ html }: { html: string }) {',
      '  return <div dangerouslySetInnerHTML={{ __html: html }} />',
      '}',
      '',
    ].join('\n')

    const [result] = await eslint.lintText(source, { filePath: sampleFile })
    const errorRules = result.messages.filter((message) => message.severity === 2).map((message) => message.ruleId)

    expect(errorRules).toContain('react/no-danger')
  })
})
