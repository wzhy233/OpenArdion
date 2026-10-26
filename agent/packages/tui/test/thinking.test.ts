import { expect, test } from "bun:test"
import type { SessionMessageInfo } from "@opencode/client"
import { thinkingLabel, waitingForContent } from "../src/util/thinking"

test("thinking dots move without changing the row width", () => {
  const frames = Array.from({ length: 6 }, (_, index) => thinkingLabel(index))
  expect(new Set(frames).size).toBeGreaterThanOrEqual(3)
  expect(frames.every((frame) => frame.startsWith("Thinking") && frame.length === 11)).toBe(true)
  expect(thinkingLabel(6)).toBe(frames[0])
})

test("wait until real content, then wait again after a completed tool step", () => {
  const message = {
    type: "assistant", time: { created: 1 }, content: [],
  } as unknown as Extract<SessionMessageInfo, { type: "assistant" }>
  expect(waitingForContent([])).toBe(true)
  expect(waitingForContent([message])).toBe(true)
  message.content = [{ type: "text", text: "hello" } as typeof message.content[number]]
  expect(waitingForContent([message])).toBe(false)
  message.content = [{ type: "reasoning", text: "checking" } as typeof message.content[number]]
  expect(waitingForContent([message])).toBe(false)
  message.time.completed = 2
  expect(waitingForContent([message])).toBe(true)
})
