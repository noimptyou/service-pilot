export type SenderType = 'CUSTOMER' | 'AGENT' | 'AI' | 'SYSTEM'

export type SessionStatus =
  | 'WAITING'
  | 'ACTIVE'
  | 'HUMAN_REQUESTED'
  | 'HUMAN_ACTIVE'
  | 'CLOSED'

export type HandoffStatus = 'PENDING' | 'ACCEPTED' | 'RESOLVED' | 'CANCELLED'

export interface SessionResponse {
  id: number
  customerName: string
  status: SessionStatus
  createdAt: string
}

export interface MessageResponse {
  id: number
  sessionId: number
  senderType: SenderType
  content: string
  createdAt: string
}

export interface KnowledgeReferenceResponse {
  knowledgeDocumentId: number
  documentTitle: string
  chunkIndex: number
  content: string
  score: number
}

export interface ChatReplyResponse {
  customerMessage: MessageResponse
  aiMessage: MessageResponse
  references: KnowledgeReferenceResponse[]
}

export interface HandoffResponse {
  id: number
  sessionId: number
  status: HandoffStatus
  reason: string
  assignedAgent: string | null
  createdAt: string
  acceptedAt: string | null
  resolvedAt: string | null
}
