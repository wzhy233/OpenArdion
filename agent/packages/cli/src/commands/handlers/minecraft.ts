import path from "node:path"
import { pathToFileURL } from "node:url"
import { Effect } from "effect"
import { Commands } from "../commands"
import { Runtime } from "../../framework/runtime"

export default Runtime.handler(
  Commands.commands.minecraft,
  Effect.fn("cli.minecraft")(function* (input) {
    yield* Effect.tryPromise({
      try: () => import(pathToFileURL(path.resolve(input.script)).href),
      catch: (error) => error instanceof Error ? error : new Error(String(error)),
    })
  }),
)
