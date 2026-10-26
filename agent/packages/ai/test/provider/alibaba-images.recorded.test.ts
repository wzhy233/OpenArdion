import { describe, expect } from "bun:test"
import { Effect } from "effect"
import { Image, Media } from "../../src/index.js"
import { Alibaba } from "../../src/providers.js"
import { recordedTests } from "../recorded-test.js"

const alibaba = Alibaba.configure({ region: "ap-southeast-1", apiKey: process.env.ALIBABA_API_KEY ?? "fixture" })

const recorded = recordedTests({
  prefix: "alibaba-images",
  provider: "alibaba",
  protocol: "alibaba-images",
  requires: ["ALIBABA_API_KEY"],
  tags: ["region:ap-southeast-1"],
})

describe("Alibaba Images recorded", () => {
  recorded.effect("generates Qwen-Image images", () =>
    Effect.gen(function* () {
      const response = yield* Image.generate({
        model: alibaba.image("qwen-image-2.0"),
        prompt: "A simple flat red circle centered on a plain white background.",
        n: 2,
        size: "512x512",
        seed: 7,
        providerOptions: { promptExtend: false, watermark: false },
      })

      expect(response.images).toHaveLength(2)
      expect(response.image.source.type === "url" && response.image.source.url).toStartWith("https://")
      expect(response.providerMetadata?.alibaba).toMatchObject({ usage: { image_count: 2, width: 512, height: 512 } })
    }),
  )

  recorded.effect("generates Wan images", () =>
    Effect.gen(function* () {
      const response = yield* Image.generate({
        model: alibaba.image("wan2.7-image"),
        prompt: "A simple flat blue square centered on a plain white background.",
        n: 2,
        size: "768x768",
        providerOptions: { thinkingMode: false },
      })

      expect(response.images).toHaveLength(2)
      expect(response.providerMetadata?.alibaba).toMatchObject({ usage: { image_count: 2, size: "768*768" } })
    }),
  )

  recorded.effect(
    "edits an image",
    () =>
      Effect.gen(function* () {
        const response = yield* Image.generate({
          model: alibaba.image("qwen-image-2.0"),
          prompt: "Keep the simple shape and change it from black to bright purple.",
          images: [
            Media.bytes(
              yield* Effect.promise(() => Bun.file("test/fixtures/images/edit-source.jpg").bytes()),
              "image/jpeg",
            ),
          ],
          size: "512x512",
        })

        expect(response.images).toHaveLength(1)
        expect(response.image.source.type).toBe("url")
      }),
    { timeout: 60_000 },
  )
})
