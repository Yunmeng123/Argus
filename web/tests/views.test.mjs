import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
const view = name => readFile(new URL(`../src/views/${name}`, import.meta.url), 'utf8')

test('review list implements loading, empty data and API error feedback', async () => {
  const source = await view('ReviewList.vue')
  assert.match(source, /v-loading="loading"/); assert.match(source, /还没有审查记录/)
  assert.match(source, /catch \(e\)[\s\S]*ElMessage\.error\(e\.message\)[\s\S]*finally[\s\S]*loading\.value = false/)
})
test('new review implements loading and submit/poll feedback actions', async () => {
  const source = await view('ReviewNew.vue')
  assert.match(source, /:loading="loading"/); assert.match(source, /ElMessage\.warning\('请先粘贴 diff 内容'\)/)
  assert.match(source, /ElMessage\.success/); assert.match(source, /ElMessage\.error/); assert.match(source, /router\.push/)
})
test('review detail handles empty findings and load errors', async () => {
  const source = await view('ReviewDetail.vue')
  assert.match(source, /本次审查未发现问题/); assert.match(source, /v-loading="loading"/); assert.match(source, /ElMessage\.error\(e\.message\)/)
})
