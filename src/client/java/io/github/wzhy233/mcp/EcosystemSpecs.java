package io.github.wzhy233.mcp;

import java.util.List;
import java.util.Map;
import static io.github.wzhy233.mcp.GameTools.*;

final class EcosystemSpecs {
    static List<GameTools.Spec> specs() {
        return List.of(
                spec("ardion_modules", "Discover all registered Ardion modules and parameters; get, enable/disable, toggle, set/reset parameters. Results contain actual before/after values.", schema(Map.of(
                        "action", prop("string", "list/get/enable/toggle/set/reset"), "module", prop("string", "Registered module name"), "query", prop("string", "List name filter"),
                        "enabled", prop("boolean", "Required for enable"), "setting", prop("string", "Parameter name for set/reset"), "value", prop("string", "New value, parsed by the existing value implementation"), "persist", prop("boolean", "Save default config after successful change")), List.of())),
                spec("ardion_settings", "List/get/set/reset global, theme, HUD and target settings through Ardion's real value objects. Contains no AI credentials.", schema(Map.of(
                        "action", prop("string", "list/get/set/reset"), "setting", prop("string", "Name from list"), "value", prop("string", "New value"), "persist", prop("boolean", "Save default config")), List.of())),
                spec("ardion_config", "List, save or load Ardion configuration; inspect/apply HUD layout. Loading applies the saved module key bindings.", schema(Map.of(
                        "action", prop("string", "list/save/load/hud_get/hud_apply"), "name", prop("string", "Config name for save/load"), "data", prop("object", "HUD config returned by hud_get")), List.of())),
                spec("ardion_command", "Inspect or execute the actual local Fabric client command tree (Ardion friends, notebot, UI, module commands, etc.). Not a server slash command.", schema(Map.of(
                        "action", prop("string", "help or execute"), "command", prop("string", "Client command without slash")), List.of())),
                spec("ardion_music", "Control Ardion's real MusicPlayer/Netease services. Playback state, search, playlists, lyrics, playback/queue transport, volume, seeking, account and QR login. Network actions use its existing account; no raw cookies or passwords are returned.", schema(Map.of(
                        "action", prop("string", "status/search/hot_search/recommend/playlists/playlist_tracks/play/play_playlist/toggle/pause/resume/stop/next/previous/seek/volume/lyrics/account/like/qr_start/qr_check/logout/open"),
                        "query", prop("string", "Search query"), "offset", prop("number", "Search offset"), "songId", prop("number", "Song ID"), "playlistId", prop("number", "Playlist ID"), "userId", prop("number", "User ID"),
                        "positionMs", prop("number", "Seek position in milliseconds"), "volume", prop("number", "0-1 volume"), "liked", prop("boolean", "Like/unlike"), "key", prop("string", "Temporary key from qr_start")), List.of())),
                spec("client_inspect", "READ ONLY: inspect runtime types, field metadata, primitive/enum instance fields and method signatures. Never invokes methods, writes fields, evaluates code or initializes target classes. Use actual runtime mapping names when diagnosing source issues.", schema(Map.of(
                        "target", prop("string", "client/player/world/module:<name>/class:<fully-qualified-name>"), "query", prop("string", "Member-name filter")), List.of("target"))),
                spec("client_threads", "READ ONLY: real JVM thread stacks, blocking state and lock owners for diagnosing freezes/deadlocks. Does not pause or interrupt threads.", schema(Map.of("query", prop("string", "Thread-name substring, e.g. Render or Server")), List.of())),
                spec("client_memory", "READ ONLY: heap/non-heap usage, class count and uptime. Does not trigger GC or change JVM state.", schema()),
                spec("client_events", "READ ONLY gameplay diagnostic probe: observe event/listener execution times, cancellation flags and errors without modifying events or calling additional listeners. Stores latest 512 records; no packet/chat payloads or secrets.", schema(Map.of(
                        "action", prop("string", "start/read/stop"), "query", prop("string", "Event class-name filter"), "seconds", prop("number", "Observation duration 1-300, default 30")), List.of("action"))),
                spec("lua", "Manage hot-reloaded Ardion Lua scripts. Query action api before writing. Actions: list, read, write, delete, check, load, unload, reload, modules, enable, disable, errors, api, status. Scripts live in the returned directory. Failed loads roll back hooks. Repair writes stop after 5 failed loads unless force is true. Scripts have full luajava access to the live JVM, including non-public fields and methods, plus io and os. Chunks compile to Java bytecode.", schema(Map.of(
                        "action", prop("string", "list/read/write/delete/check/load/unload/reload/modules/enable/disable/errors/api/status"),
                        "name", prop("string", "Script name, letters digits _ -, optional .lua"),
                        "source", prop("string", "Full script source for write/check"),
                        "force", prop("boolean", "Write past the repair limit")), List.of("action")))
        );
    }
}
