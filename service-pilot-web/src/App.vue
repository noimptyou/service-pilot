<script setup lang="ts">
import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  createSession,
  getApiErrorMessage,
  getLatestHandoff,
  getMessages,
  requestHandoff,
  sendChatMessage,
  sendCustomerMessage,
} from './api/conversation'
import { subscribeConversationEvents } from './api/conversation-events'
import type {
  HandoffResponse,
  KnowledgeReferenceResponse,
  MessageResponse,
  SessionResponse,
} from './types/conversation'

const SESSION_STORAGE_KEY = 'service-pilot-session'
const customerName = ref('')
const draft = ref('')
const session = ref<SessionResponse | null>(readStoredSession())
const messages = ref<MessageResponse[]>([])
const handoff = ref<HandoffResponse | null>(null)
const referencesByMessageId = ref<Record<number, KnowledgeReferenceResponse[]>>({})
const errorMessage = ref('')
const isCreating = ref(false)
const isSending = ref(false)
const isRequestingHandoff = ref(false)
const isRefreshing = ref(false)
const messagePanel = ref<HTMLElement | null>(null)
let refreshQueued = false
let unsubscribeConversationEvents: (() => void) | undefined

const markdown = new MarkdownIt({
  breaks: false,
  html: false,
  linkify: true,
})

const isHumanConversation = computed(() =>
  handoff.value?.status === 'PENDING' || handoff.value?.status === 'ACCEPTED',
)

const statusLabel = computed(() => {
  if (handoff.value?.status === 'PENDING') return '等待人工客服'
  if (handoff.value?.status === 'ACCEPTED') {
    return `${handoff.value.assignedAgent ?? '人工客服'}正在服务`
  }
  if (handoff.value?.status === 'RESOLVED' || session.value?.status === 'CLOSED') {
    return '会话已结束'
  }
  return 'AI 客服在线'
})

const statusTone = computed(() => {
  if (handoff.value?.status === 'PENDING') return 'waiting'
  if (handoff.value?.status === 'ACCEPTED') return 'human'
  if (handoff.value?.status === 'RESOLVED') return 'closed'
  return 'online'
})

onMounted(async () => {
  if (!session.value) return
  customerName.value = session.value.customerName
  connectConversationEvents()
  await refreshConversation()
})

onBeforeUnmount(() => disconnectConversationEvents())

async function handleCreateSession() {
  const normalizedName = customerName.value.trim()
  if (!normalizedName || isCreating.value) return
  errorMessage.value = ''
  isCreating.value = true
  try {
    const created = await createSession(normalizedName)
    session.value = created
    messages.value = []
    handoff.value = null
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(created))
    connectConversationEvents()
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error)
  } finally {
    isCreating.value = false
  }
}

async function handleSend() {
  const content = draft.value.trim()
  if (
    !session.value
    || !content
    || isSending.value
    || session.value.status === 'CLOSED'
  ) return
  const optimisticMessage: MessageResponse = {
    id: -Date.now(),
    sessionId: session.value.id,
    senderType: 'CUSTOMER',
    content,
    createdAt: new Date().toISOString(),
  }
  errorMessage.value = ''
  draft.value = ''
  isSending.value = true
  messages.value = [...messages.value, optimisticMessage]
  await scrollToLatest()
  try {
    if (isHumanConversation.value) {
      const savedMessage = await sendCustomerMessage(session.value.id, content)
      removeMessage(optimisticMessage.id)
      mergeMessages([savedMessage])
    } else {
      const reply = await sendChatMessage(session.value.id, content)
      removeMessage(optimisticMessage.id)
      mergeMessages([reply.customerMessage, reply.aiMessage])
      referencesByMessageId.value[reply.aiMessage.id] = reply.references
      await refreshHandoff()
    }
  } catch (error) {
    removeMessage(optimisticMessage.id)
    errorMessage.value = getApiErrorMessage(error)
    draft.value = content
    await refreshConversation(true)
  } finally {
    isSending.value = false
    await scrollToLatest()
  }
}

async function handleRequestHandoff() {
  if (
    !session.value
    || isRequestingHandoff.value
    || isHumanConversation.value
    || session.value.status === 'CLOSED'
  ) return
  errorMessage.value = ''
  isRequestingHandoff.value = true
  try {
    handoff.value = await requestHandoff(
      session.value.id,
      '客户在聊天页面主动申请人工客服',
    )
    session.value.status = 'HUMAN_REQUESTED'
    persistSession()
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error)
  } finally {
    isRequestingHandoff.value = false
  }
}

async function refreshConversation(silent = false) {
  if (!session.value) return
  if (isRefreshing.value) {
    refreshQueued = true
    return
  }
  if (!silent) errorMessage.value = ''
  isRefreshing.value = true
  try {
    const [history, latestHandoff] = await Promise.all([
      getMessages(session.value.id),
      getLatestHandoff(session.value.id),
    ])
    messages.value = history
    handoff.value = latestHandoff
    syncSessionStatus()
    await scrollToLatest()
  } catch (error) {
    if (!silent) errorMessage.value = getApiErrorMessage(error)
  } finally {
    isRefreshing.value = false
    if (refreshQueued) {
      refreshQueued = false
      void refreshConversation(true)
    }
  }
}

async function refreshHandoff() {
  if (!session.value) return
  handoff.value = await getLatestHandoff(session.value.id)
  syncSessionStatus()
}

function syncSessionStatus() {
  if (!session.value || !handoff.value) return
  if (handoff.value.status === 'PENDING') session.value.status = 'HUMAN_REQUESTED'
  else if (handoff.value.status === 'ACCEPTED') session.value.status = 'HUMAN_ACTIVE'
  else if (handoff.value.status === 'RESOLVED') {
    session.value.status = 'CLOSED'
    disconnectConversationEvents()
  }
  persistSession()
}

function mergeMessages(incoming: MessageResponse[]) {
  const byId = new Map(messages.value.map((message) => [message.id, message]))
  incoming.forEach((message) => byId.set(message.id, message))
  messages.value = [...byId.values()].sort((left, right) => left.id - right.id)
}

function removeMessage(messageId: number) {
  messages.value = messages.value.filter((message) => message.id !== messageId)
}

function resetSession() {
  disconnectConversationEvents()
  refreshQueued = false
  localStorage.removeItem(SESSION_STORAGE_KEY)
  session.value = null
  messages.value = []
  handoff.value = null
  referencesByMessageId.value = {}
  draft.value = ''
  errorMessage.value = ''
}

function readStoredSession(): SessionResponse | null {
  const stored = localStorage.getItem(SESSION_STORAGE_KEY)
  if (!stored) return null
  try {
    return JSON.parse(stored) as SessionResponse
  } catch {
    localStorage.removeItem(SESSION_STORAGE_KEY)
    return null
  }
}

function persistSession() {
  if (session.value) localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session.value))
}

function connectConversationEvents() {
  disconnectConversationEvents()
  if (!session.value || session.value.status === 'CLOSED') return
  unsubscribeConversationEvents = subscribeConversationEvents(
    session.value.id,
    () => void refreshConversation(true),
  )
}

function disconnectConversationEvents() {
  unsubscribeConversationEvents?.()
  unsubscribeConversationEvents = undefined
}

async function scrollToLatest() {
  await nextTick()
  if (messagePanel.value) messagePanel.value.scrollTop = messagePanel.value.scrollHeight
}

function senderName(message: MessageResponse) {
  if (message.senderType === 'CUSTOMER') return session.value?.customerName ?? '客户'
  if (message.senderType === 'AGENT') return handoff.value?.assignedAgent ?? '人工客服'
  if (message.senderType === 'SYSTEM') return '系统'
  return 'ServicePilot AI'
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function renderMessageContent(content: string) {
  return DOMPurify.sanitize(markdown.render(content))
}
</script>

<template>
  <main v-if="!session" class="welcome-page">
    <section class="welcome-copy">
      <div class="brand-mark">SP</div>
      <p class="eyebrow">ServicePilot 智能客服</p>
      <h1>问题有人听见，<br />答案有据可查。</h1>
      <p class="welcome-description">
        AI 客服会结合知识库与业务工具为你解答；需要进一步处理时，可以随时转接人工客服。
      </p>
      <div class="feature-list">
        <span>知识库回答</span><span>订单查询</span><span>人工接管</span>
      </div>
    </section>

    <section class="start-card">
      <p class="card-kicker">开始咨询</p>
      <h2>你好，请先告诉我怎么称呼你</h2>
      <p class="card-description">创建会话后，你的聊天记录会保存在当前客服会话中。</p>
      <form @submit.prevent="handleCreateSession">
        <label for="customer-name">客户名称</label>
        <input id="customer-name" v-model="customerName" autocomplete="name" maxlength="100" placeholder="例如：张三" />
        <p v-if="errorMessage" class="error-banner">{{ errorMessage }}</p>
        <button class="primary-button" :disabled="!customerName.trim() || isCreating">
          {{ isCreating ? '正在创建…' : '进入客服会话' }}
        </button>
      </form>
      <p class="start-note">继续即表示创建一次新的客服会话</p>
    </section>
  </main>

  <main v-else class="chat-page">
    <aside class="chat-sidebar">
      <div>
        <div class="sidebar-brand">
          <div class="brand-mark small">SP</div>
          <div><strong>ServicePilot</strong><span>智能客服中心</span></div>
        </div>
        <div class="session-card">
          <span>当前客户</span><strong>{{ session.customerName }}</strong><small>会话 #{{ session.id }}</small>
        </div>
      </div>
      <div class="sidebar-actions">
        <button class="secondary-button" @click="refreshConversation()">刷新聊天记录</button>
        <button class="text-button" @click="resetSession">退出当前会话</button>
      </div>
    </aside>

    <section class="conversation">
      <header class="conversation-header">
        <div><p class="eyebrow">在线咨询</p><h2>{{ isHumanConversation ? '人工客服会话' : 'ServicePilot AI 客服' }}</h2></div>
        <div class="status-pill" :class="statusTone"><span></span>{{ statusLabel }}</div>
      </header>

      <div ref="messagePanel" class="message-panel">
        <div v-if="messages.length === 0" class="empty-chat">
          <div class="assistant-avatar">AI</div>
          <h3>你好，{{ session.customerName }}</h3>
          <p>你可以询问平台规则、退款说明或订单状态，也可以随时申请人工客服。</p>
          <div class="suggestions">
            <button @click="draft = '你们支持七天无理由退款吗？'">七天无理由退款</button>
            <button @click="draft = '我想查询我的订单状态'">查询订单状态</button>
          </div>
        </div>

        <article
          v-for="message in messages"
          :key="message.id"
          class="message-row"
          :class="message.senderType.toLowerCase()"
        >
          <div class="message-avatar">
            {{ message.senderType === 'CUSTOMER' ? session.customerName.slice(0, 1) : message.senderType === 'AGENT' ? '人' : 'AI' }}
          </div>
          <div class="message-content">
            <div class="message-meta">
              <strong>{{ senderName(message) }}</strong>
              <time>{{ formatTime(message.createdAt) }}</time>
            </div>
            <div class="message-bubble markdown-body" v-html="renderMessageContent(message.content)"></div>
            <div v-if="referencesByMessageId[message.id]?.length" class="reference-list">
              <span>回答依据</span>
              <details
                v-for="reference in referencesByMessageId[message.id]"
                :key="`${reference.knowledgeDocumentId}-${reference.chunkIndex}`"
              >
                <summary>{{ reference.documentTitle }} <small>相似度 {{ Math.round(reference.score * 100) }}%</small></summary>
                <p>{{ reference.content }}</p>
              </details>
            </div>
          </div>
        </article>

        <article v-if="isSending && !isHumanConversation" class="message-row ai">
          <div class="message-avatar">AI</div>
          <div class="message-content">
            <div class="message-meta"><strong>ServicePilot AI</strong></div>
            <div class="message-bubble typing"><i></i><i></i><i></i></div>
          </div>
        </article>
      </div>

      <div class="conversation-controls">
        <p v-if="errorMessage" class="error-banner chat-error">{{ errorMessage }}</p>

        <div v-if="session.status === 'CLOSED'" class="handoff-banner">
          <div>
            <strong>会话已结束</strong>
            <span>如需继续咨询，请退出当前会话后重新创建。</span>
          </div>
        </div>

        <template v-else>
          <div v-if="isHumanConversation" class="handoff-banner">
            <div>
              <strong>{{ statusLabel }}</strong>
              <span v-if="handoff?.status === 'PENDING'">请稍候，人工客服接单后会在这里回复；你仍可继续补充消息。</span>
              <span v-else>AI 已暂停回复，当前消息由人工客服处理。</span>
            </div>
            <button class="secondary-button" @click="refreshConversation()">刷新</button>
          </div>

          <footer class="composer">
            <textarea
              v-model="draft"
              rows="1"
              maxlength="2000"
              :placeholder="isHumanConversation ? '输入要发送给人工客服的消息，Enter 发送' : '输入你的问题，Enter 发送，Shift + Enter 换行'"
              @keydown.enter.exact.prevent="handleSend"
            ></textarea>
            <div class="composer-actions">
              <button
                v-if="!isHumanConversation"
                class="handoff-button"
                :disabled="isRequestingHandoff"
                @click="handleRequestHandoff"
              >
                {{ isRequestingHandoff ? '正在申请…' : '转人工客服' }}
              </button>
              <span v-else class="human-mode-label">正在与人工客服对话</span>
              <button class="send-button" :disabled="!draft.trim() || isSending" @click="handleSend">发送</button>
            </div>
          </footer>
        </template>
      </div>
    </section>
  </main>
</template>
