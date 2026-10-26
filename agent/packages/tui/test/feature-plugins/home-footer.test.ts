import { describe, expect, test } from "bun:test"
import { homeFooterVisibility } from "../../src/feature-plugins/home/footer"

describe("home footer visibility", () => {
  test("keeps failure labels readable at the minimum supported width", () => {
    expect(homeFooterVisibility(44)).toEqual({ mcpCommand: false, pluginCommand: false })
  })

  test("adds secondary hints as space becomes available", () => {
    expect(homeFooterVisibility(64)).toEqual({ mcpCommand: true, pluginCommand: false })
    expect(homeFooterVisibility(80)).toEqual({ mcpCommand: true, pluginCommand: true })
  })
})
