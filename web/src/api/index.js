import axios from 'axios'
import { ElMessageBox } from 'element-plus'

// LLM 审查多文件时较慢, 超时放宽到 5 分钟
const http = axios.create({ timeout: 300000 })

const TOKEN_KEY = 'argus_token'

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers['X-Argus-Token'] = token
  }
  return config
})

let promptingToken = false

http.interceptors.response.use(
  (resp) => resp.data,
  async (err) => {
    // 服务端开启了访问令牌鉴权: 引导输入并存储, 之后所有请求自动携带
    if (err.response?.status === 401 && !promptingToken) {
      promptingToken = true
      try {
        const { value } = await ElMessageBox.prompt('服务端已开启访问控制, 请输入访问令牌(ARGUS_ACCESS_TOKEN)', '需要访问令牌', {
          inputType: 'password',
          confirmButtonText: '保存并重试',
          cancelButtonText: '取消'
        })
        if (value) {
          localStorage.setItem(TOKEN_KEY, value.trim())
          location.reload()
        }
      } catch {
        /* 用户取消 */
      } finally {
        promptingToken = false
      }
    }
    const msg = err.response?.data?.error || err.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

export const reviewDiff = (diffText) =>
  http.post('/api/review/diff', diffText, { headers: { 'Content-Type': 'text/plain;charset=UTF-8' } })

export const reviewLocal = (payload) => http.post('/api/review/local', payload)

// 异步任务: 提交秒回 jobId, 轮询进度
export const submitReviewJob = (payload) => http.post('/api/review/jobs', payload)

export const getReviewJob = (jobId) => http.get(`/api/review/jobs/${jobId}`)

export const listReviews = () => http.get('/api/reviews')

export const getReview = (id) => http.get(`/api/reviews/${id}`)

export const getConfig = () => http.get('/api/config')

export const updateConfig = (payload) => http.put('/api/config', payload)

export const getStats = () => http.get('/api/stats')

export const runEval = () => http.post('/api/eval/run')

export const listAuthors = () => http.get('/api/authors')

export const getAuthorProfile = (author) => http.get(`/api/authors/${encodeURIComponent(author)}`)

export const genAuthorAiSummary = (author) => http.post(`/api/authors/${encodeURIComponent(author)}/ai-summary`)
