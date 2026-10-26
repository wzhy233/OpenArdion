import type { SessionMessageInfo } from "@opencode/client"

export function thinkingLabel(frame: number) {
  return "Thinking" + [".  ", ".. ", "...", " ..", "  .", " .."][frame % 6]
}

export function waitingForContent(messages: readonly SessionMessageInfo[]) {
  const last = messages.findLast((message) => message.type === "assistant" || message.type === "user")
  if (last?.type !== "assistant" || last.time.completed !== undefined) return true
  const part = last.content.at(-1)
  if (part?.type === "text" || part?.type === "reasoning") return !part.text.trim()
  if (part?.type === "tool") return part.state.status !== "running" && part.state.status !== "streaming"
  return true
}
