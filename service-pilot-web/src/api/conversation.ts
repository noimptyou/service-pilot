import axios from 'axios'
import type {
  ChatReplyResponse,
  HandoffResponse,
  MessageResponse,
  SessionResponse,
} from '../types/conversation'

const api = axios.create({
  baseURL: '/api',
  timeout: 60_000,
})

export async function createSession(customerName: string): Promise<SessionResponse> {
  const response = await api.post<SessionResponse>('/conversations', { customerName })
  return response.data
}

export async function sendChatMessage(
  sessionId: number,
  content: string,
): Promise<ChatReplyResponse> {
  const response = await api.post<ChatReplyResponse>(
    `/conversations/${sessionId}/chat`,
    { content },
  )
  return response.data
}

export async function sendCustomerMessage(
  sessionId: number,
  content: string,
): Promise<MessageResponse> {
  const response = await api.post<MessageResponse>(
    `/conversations/${sessionId}/messages`,
    { content },
  )
  return response.data
}

export async function getMessages(sessionId: number): Promise<MessageResponse[]> {
  const response = await api.get<MessageResponse[]>(
    `/conversations/${sessionId}/messages`,
  )
  return response.data
}

export async function requestHandoff(
  sessionId: number,
  reason: string,
): Promise<HandoffResponse> {
  const response = await api.post<HandoffResponse>(
    `/conversations/${sessionId}/handoff`,
    { reason },
  )
  return response.data
}

export async function getLatestHandoff(
  sessionId: number,
): Promise<HandoffResponse | null> {
  try {
    const response = await api.get<HandoffResponse>(
      `/conversations/${sessionId}/handoff`,
    )
    return response.data
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      return null
    }
    throw error
  }
}

export function getApiErrorMessage(error: unknown): string {
  if (!axios.isAxiosError(error)) {
    return '操作失败，请稍后重试'
  }

  if (!error.response) {
    return '无法连接后端，请确认 ServicePilotApplication 已启动'
  }

  const status = error.response.status
  if (status === 409) {
    return '当前会话已经转入人工处理，AI 客服不再回复'
  }
  if (status === 502) {
    return 'AI 客服暂时无法回复，您的问题已经保存'
  }
  if (status === 503) {
    return 'AI 客服尚未启用，请检查 AI 配置'
  }
  return `请求失败（HTTP ${status}）`
}
