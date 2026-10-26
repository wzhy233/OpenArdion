import { Effect, Option } from "effect"
import { Commands } from "../commands"
import { Runtime } from "../../framework/runtime"
import { ServerConnection } from "../../services/server-connection"

export default Runtime.handler(Commands.commands.run, (input) =>
  Effect.gen(function* () {
    const { runNonInteractive } = yield* Effect.promise(() => import("../../run/run"))
    const separator = process.argv.indexOf("--", 2)
    const tail = separator === -1 ? [] : process.argv.slice(separator + 1)
    const parsedTail = tail.length > 0 && tail.every((part, index) => input.message[input.message.length - tail.length + index] === part)
    const server = yield* ServerConnection.resolve()
    yield* Effect.promise(() =>
      runNonInteractive({
        server,
        message: parsedTail ? input.message : [...input.message, ...tail],
        continue: input.continue,
        session: Option.getOrUndefined(input.session),
        fork: input.fork,
        model: Option.getOrUndefined(input.model),
        agent: Option.getOrUndefined(input.agent),
        format: input.format,
        file: [...input.file],
        title: Option.getOrUndefined(input.title),
        thinking: input.thinking,
        auto: input.auto || input.yolo || input.dangerouslySkipPermissions,
      }),
    )
  }),
)
