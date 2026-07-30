import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import vm from 'node:vm'

async function client() {
  const source = (await readFile(new URL('../src/api/index.js', import.meta.url), 'utf8'))
    .replace("import axios from 'axios'", 'const axios = globalThis.axios')
    .replace("import { ElMessageBox } from 'element-plus'", 'const { ElMessageBox } = globalThis')
    .replaceAll('export const ', 'globalThis.exports.')
  const calls = []; let requestInterceptor; let errorInterceptor
  const http = {
    interceptors: { request: { use(fn) { requestInterceptor = fn } }, response: { use(_, bad) { errorInterceptor = bad } } },
    get: (...args) => calls.push(['get', ...args]), post: (...args) => calls.push(['post', ...args]), put: (...args) => calls.push(['put', ...args])
  }
  const storage = new Map()
  const context = { exports: {}, axios: { create: () => http }, ElMessageBox: { prompt: async () => { throw new Error('cancel') } },
    localStorage: { getItem: k => storage.get(k), setItem: (k, v) => storage.set(k, v) }, location: { reload() {} }, Error, Promise, console }
  vm.runInNewContext(source, context)
  return { ...context, calls, requestInterceptor, errorInterceptor }
}

test('API functions use expected endpoints and encode authors', async () => {
  const c = await client(); c.exports.reviewDiff('diff'); c.exports.updateConfig({ mock: true }); c.exports.getAuthorProfile('a/b')
  assert.equal(c.calls[0][1], '/api/review/diff'); assert.equal(c.calls[1][1], '/api/config'); assert.equal(c.calls[2][1], '/api/authors/a%2Fb')
})
test('request interceptor adds stored token', async () => {
  const c = await client(); c.localStorage.setItem('argus_token', 'secret'); const config = c.requestInterceptor({ headers: {} })
  assert.equal(config.headers['X-Argus-Token'], 'secret')
})
test('response interceptor exposes API and network errors', async () => {
  const c = await client()
  await assert.rejects(c.errorInterceptor({ response: { status: 400, data: { error: 'invalid diff' } } }), /invalid diff/)
  await assert.rejects(c.errorInterceptor({ message: 'offline' }), /offline/)
})
