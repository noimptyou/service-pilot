export function subscribeConversationEvents(
  sessionId: number,
  onConversationChanged: () => void,
) {
  const eventSource = new EventSource(
    `/api/conversations/${sessionId}/events`,
  )

  eventSource.addEventListener('message-created', onConversationChanged)
  eventSource.addEventListener('conversation-state-changed', onConversationChanged)

  return () => eventSource.close()
}
