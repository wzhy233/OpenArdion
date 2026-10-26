import os from "os"
import path from "path"
import { existsSync, mkdirSync, renameSync } from "node:fs"

const home = os.homedir()
const data = process.env.XDG_DATA_HOME || (home ? path.join(home, ".local", "share") : undefined)
const cache = process.env.XDG_CACHE_HOME || (home ? path.join(home, ".cache") : undefined)
const config = process.env.XDG_CONFIG_HOME || (home ? path.join(home, ".config") : undefined)
const state = process.env.XDG_STATE_HOME || (home ? path.join(home, ".local", "state") : undefined)

declare const ARDION_RUNTIME: boolean | undefined
const ardion = typeof ARDION_RUNTIME !== "undefined" && ARDION_RUNTIME
const ardionHome = process.env.ARDION_HOME || path.join(home, ".ardion")
const ardionRoot = path.join(ardionHome, "agent")
if (ardion) {
  mkdirSync(ardionRoot, { recursive: true })
  // Move only Ardion's previous private store; ordinary OpenCode directories are never read.
  for (const kind of ["config", "data", "state", "cache", "tmp"]) {
    const old = path.join(ardionRoot, "opencode", kind, ...(kind === "tmp" ? [] : ["opencode"]))
    const target = path.join(ardionRoot, kind)
    if (!existsSync(target) && existsSync(old)) renameSync(old, target)
  }
  for (const [kind, before, after] of [
    ["config", "opencode.json", "ardion.json"], ["config", "opencode.jsonc", "ardion.jsonc"],
    ["data", "opencode.db", "ardion.db"], ["data", "opencode.db-wal", "ardion.db-wal"], ["data", "opencode.db-shm", "ardion.db-shm"],
  ]) {
    const old = path.join(ardionRoot, kind, before)
    const target = path.join(ardionRoot, kind, after)
    if (existsSync(old) && !existsSync(target)) renameSync(old, target)
  }
  process.env.ARDION_HOME = ardionHome
  delete process.env.OPENCODE_CONFIG
  delete process.env.OPENCODE_CLI_CONFIG_CONTENT
  delete process.env.OPENCODE_TEST_HOME
  process.env.OPENCODE_CONFIG_DIR = path.join(ardionRoot, "config")
  process.env.OPENCODE_DB = path.join(ardionRoot, "data", "ardion.db")
  process.env.OPENCODE_CONFIG_PROJECT_DISABLE = "1"
  process.env.OPENCODE_DISABLE_AUTOUPDATE = "1"
  process.env.OPENCODE_CONFIG_CONTENT = process.env.ARDION_CONFIG_CONTENT || "{}"
}

/** The XDG base directories that root opencode's global paths. */
export function roots(app: string) {
  if (ardion) return {
    data: path.join(ardionRoot, "data"),
    cache: path.join(ardionRoot, "cache"),
    config: path.join(ardionRoot, "config"),
    state: path.join(ardionRoot, "state"),
    tmp: path.join(ardionRoot, "tmp"),
  }
  return {
    data: path.join(data!, app),
    cache: path.join(cache!, app),
    config: path.join(config!, app),
    state: path.join(state!, app),
    tmp: path.join(os.tmpdir(), app),
  }
}
