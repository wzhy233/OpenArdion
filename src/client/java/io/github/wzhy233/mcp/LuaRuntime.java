package io.github.wzhy233.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.wzhy233.ardion.event.base.Event;
import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.impl.network.ChatReceiveEvent;
import io.github.wzhy233.ardion.event.impl.world.WorldLoadEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.event.manager.EventManager;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.util.client.ArdionHome;
import net.minecraft.client.MinecraftClient;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.LuaJavaBridge;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public final class LuaRuntime {
    private static final int LIMIT = 65_536;
    private static final int REPAIRS = 5;
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final List<Hook> HOOKS = new ArrayList<>();
    private static final List<Task> TASKS = new ArrayList<>();
    private static final Map<String, ScriptModule> SCRIPTS = new HashMap<>();
    private static final Map<String, Integer> FAILURES = new HashMap<>();
    private static final ArrayDeque<String> ERRORS = new ArrayDeque<>();
    private static final Set<String> STACK = new HashSet<>();
    private static final ThreadLocal<String> OWNER = new ThreadLocal<>();
    private static final String[] EVENT_PACKAGES = {"client", "network", "player", "render", "world"};
    public static volatile Set<Class<?>> watched;
    private static Globals globals;
    private static boolean listening;

    private LuaRuntime() {
    }

    public static void loadAll() {
        LOCK.lock();
        try {
            Files.createDirectories(directory());
            try (var files = Files.list(directory())) {
                for (Path path : files.filter(Files::isRegularFile)
                        .filter(file -> file.getFileName().toString().endsWith(".lua")).sorted().toList()) {
                    String name = path.getFileName().toString();
                    try {
                        String script = scriptName(name);
                        if (!SCRIPTS.containsKey(script)) load(script);
                    } catch (Exception error) {
                        remember(name, error);
                    }
                }
            }
        } catch (IOException error) {
            remember("startup", error);
        } finally { LOCK.unlock(); }
    }

    private static boolean enabled(String name) {
        ScriptModule module = SCRIPTS.get(name);
        return module != null && module.isEnabled();
    }

    private static String moduleName(String name) {
        ScriptModule module = SCRIPTS.get(name);
        return module == null ? "" : module.getName();
    }

    public static String call(JsonObject args) throws Exception {
        MinecraftClient client = client();
        if (client != null && !client.isOnThread()) return GameToolSupport.onClient(() -> {
            try { return call(args); }
            catch (RuntimeException error) { throw error; }
            catch (Exception error) { throw new IllegalStateException(error.getMessage(), error); }
        });
        LOCK.lock();
        try { return invoke(args == null ? new JsonObject() : args); }
        finally { LOCK.unlock(); }
    }

    public static void dispatch(String event, String text) {
        if (!LOCK.tryLock()) return;
        try { dispatchLocked(event, text == null ? LuaValue.NIL : LuaValue.valueOf(text)); }
        finally { LOCK.unlock(); }
    }

    public static void onWatched(Event event) {
        if (event == null || SCRIPTS.isEmpty() || !LOCK.tryLock()) return;
        try {
            String simple = event.getClass().getSimpleName();
            String binary = event.getClass().getName();
            LuaValue java = LuaJavaBridge.toLua(event);
            dispatchLocked(simple, java);
            if (!binary.equals(simple)) dispatchLocked(binary, java);
        } finally { LOCK.unlock(); }
    }

    private static boolean hooked(LuaValue fn) {
        synchronized (HOOKS) {
            for (Hook hook : HOOKS) if (hook.fn == fn) return true;
        }
        return false;
    }

    private static void dispatchLocked(String event, LuaValue arg) {
        List<Hook> hooks;
        synchronized (HOOKS) { hooks = List.copyOf(HOOKS); }
        for (Hook hook : hooks) {
            if (!hook.event.equals(event) || !enabled(hook.script)) continue;
            callHook(hook.script, hook.fn, arg);
        }
    }

    private static String invoke(JsonObject args) throws Exception {
        String action = args.has("action") ? GameToolSupport.text(args, "action") : "list";
        return switch (action) {
            case "list" -> list();
            case "read" -> read(script(args));
            case "write" -> write(script(args), source(args), force(args));
            case "delete" -> delete(script(args));
            case "check" -> check(script(args), source(args));
            case "load" -> load(script(args));
            case "unload" -> unload(script(args));
            case "reload" -> reload(script(args));
            case "modules" -> modules();
            case "enable", "disable" -> setEnabled(script(args), action.equals("enable"));
            case "errors" -> errors();
            case "api" -> api();
            case "status" -> status(args.has("name") ? script(args) : "");
            default -> throw new IllegalArgumentException("Unknown lua action");
        };
    }

    private static String list() throws IOException {
        Path dir = directory();
        Files.createDirectories(dir);
        JsonArray rows = new JsonArray();
        try (var files = Files.list(dir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".lua")).sorted().forEach(path -> {
                String name = path.getFileName().toString().replaceFirst("\\.lua$", "");
                JsonObject row = new JsonObject();
                row.addProperty("name", name);
                row.addProperty("loaded", SCRIPTS.containsKey(name));
                row.addProperty("enabled", enabled(name));
                row.addProperty("module", moduleName(name));
                row.addProperty("failures", FAILURES.getOrDefault(name, 0));
                rows.add(row);
            });
        }
        JsonObject result = new JsonObject();
        result.addProperty("directory", dir.toString());
        result.add("scripts", rows);
        return result.toString();
    }

    private static String read(String name) throws IOException {
        Path file = file(name);
        if (!Files.isRegularFile(file)) throw new IllegalArgumentException("No script: " + name);
        String text = Files.readString(file);
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        result.addProperty("source", text);
        return result.toString();
    }

    private static String write(String name, String source, boolean force) throws IOException {
        if (!force && FAILURES.getOrDefault(name, 0) >= REPAIRS) {
            throw new IllegalStateException("Repair limit reached for " + name + " (" + REPAIRS + " failed loads). Change approach or pass force:true");
        }
        if (source.getBytes(StandardCharsets.UTF_8).length > LIMIT) throw new IllegalArgumentException("Script is larger than " + LIMIT + " bytes");
        Path file = file(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        result.addProperty("bytes", source.getBytes(StandardCharsets.UTF_8).length);
        result.addProperty("written", true);
        return result.toString();
    }

    private static String delete(String name) throws IOException {
        unload(name);
        Files.deleteIfExists(file(name));
        FAILURES.remove(name);
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        result.addProperty("deleted", true);
        return result.toString();
    }

    private static String check(String name, String source) {
        try {
            globals().load(source, name);
        } catch (LuaError error) {
            remember(name, error);
            throw new IllegalStateException(name + " syntax: " + error.getMessage(), error);
        }
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        result.addProperty("ok", true);
        return result.toString();
    }

    private static String load(String name) throws IOException {
        if (!STACK.add(name)) throw new IllegalStateException("Lua require cycle: " + name);
        List<Hook> saved;
        List<Task> savedTasks;
        synchronized (HOOKS) {
            saved = new ArrayList<>(HOOKS);
            savedTasks = new ArrayList<>(TASKS);
            HOOKS.removeIf(hook -> hook.script.equals(name));
            TASKS.removeIf(task -> task.script.equals(name));
        }
        refreshWatched();
        ScriptModule previousModule = SCRIPTS.get(name);
        String previousOwner = OWNER.get();
        try {
            Path file = file(name);
            if (!Files.isRegularFile(file)) throw new IllegalArgumentException("No script: " + name);
            String source = Files.readString(file);
            if (source.getBytes(StandardCharsets.UTF_8).length > LIMIT) throw new IllegalArgumentException("Script is larger than " + LIMIT + " bytes");
            ensureListener();
            OWNER.set(name);
            Globals state = new Globals();
            install(state);
            Map<String, LuaValue> before = onFunctions(state);
            LuaValue value = state.load(source, name).call();
            onFunctions(state).forEach((function, body) -> {
                if (body != before.get(function) && !hooked(body)) add(name, eventName(function), body);
            });
            if (value.istable()) {
                add(name, "tick", value.get("onTick"));
                add(name, "enable", value.get("onEnable"));
                add(name, "disable", value.get("onDisable"));
                add(name, "chat", value.get("onChat"));
                add(name, "world", value.get("onWorld"));
            }
            String moduleName = value.istable() && value.get("name").isstring() ? value.get("name").tojstring() : name;
            if (moduleName.isBlank()) throw new LuaError("Module name cannot be blank");
            Module conflict = ModuleManager.getModule(moduleName);
            if (conflict != null && conflict != previousModule) throw new LuaError("Module already exists: " + moduleName);
            boolean wasEnabled = previousModule != null && previousModule.isEnabled();
            if (previousModule == null || !previousModule.getName().equals(moduleName)) {
                ScriptModule module = new ScriptModule(name, moduleName);
                if (previousModule != null) {
                    if (wasEnabled) lifecycle(name, false, saved);
                    SCRIPTS.remove(name);
                    ModuleManager.unregister(previousModule);
                    module.setBindKey(previousModule.getBindKey());
                }
                SCRIPTS.put(name, module);
                ModuleManager.register(module);
                if (wasEnabled) module.setEnabledSilently(true);
            } else if (wasEnabled) {
                lifecycle(name, true, List.copyOf(HOOKS));
            }
            FAILURES.remove(name);
            AgentNotify.post("success", "Lua loaded " + name);
            return status(name);
        } catch (Exception error) {
            synchronized (HOOKS) {
                HOOKS.removeIf(hook -> hook.script.equals(name));
                TASKS.removeIf(task -> task.script.equals(name));
                saved.stream().filter(hook -> hook.script.equals(name)).forEach(HOOKS::add);
                savedTasks.stream().filter(task -> task.script.equals(name)).forEach(TASKS::add);
            }
            refreshWatched();
            if (!(error instanceof RuntimeException)) throw error;
            failed(name, error);
            AgentNotify.post("error", "Lua load failed " + name + ": " + error.getMessage());
            throw new IllegalStateException(name + ": " + error.getMessage(), error);
        } finally {
            if (previousOwner == null) OWNER.remove(); else OWNER.set(previousOwner);
            STACK.remove(name);
        }
    }

    private static String unload(String name) {
        ScriptModule module = SCRIPTS.get(name);
        if (module != null) {
            ModuleManager.unregister(module);
            SCRIPTS.remove(name);
        }
        synchronized (HOOKS) {
            HOOKS.removeIf(hook -> hook.script.equals(name));
            TASKS.removeIf(task -> task.script.equals(name));
        }
        refreshWatched();
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        result.addProperty("loaded", false);
        return result.toString();
    }

    private static String reload(String name) throws IOException {
        return load(name);
    }

    private static String setEnabled(String name, boolean on) {
        ScriptModule module = SCRIPTS.get(name);
        if (module == null) throw new IllegalArgumentException("Script is not loaded: " + name);
        module.setEnabledSilently(on);
        AgentNotify.post(on ? "success" : "info", (on ? "Lua enabled " : "Lua disabled ") + name);
        return status(name);
    }

    private static String modules() {
        JsonArray rows = new JsonArray();
        SCRIPTS.forEach((name, module) -> {
            JsonObject row = new JsonObject();
            row.addProperty("script", name);
            row.addProperty("module", module.getName());
            row.addProperty("enabled", module.isEnabled());
            rows.add(row);
        });
        JsonObject result = new JsonObject();
        result.add("modules", rows);
        return result.toString();
    }

    private static String errors() {
        JsonArray rows = new JsonArray();
        synchronized (ERRORS) { ERRORS.forEach(rows::add); }
        JsonObject result = new JsonObject();
        result.add("errors", rows);
        return result.toString();
    }

    private static String status(String name) {
        JsonObject result = new JsonObject();
        result.addProperty("version", "2");
        if (!name.isEmpty()) {
            result.addProperty("name", name);
            result.addProperty("loaded", SCRIPTS.containsKey(name));
            result.addProperty("enabled", enabled(name));
            result.addProperty("module", moduleName(name));
            result.addProperty("failures", FAILURES.getOrDefault(name, 0));
        }
        int hooks;
        int tasks;
        synchronized (HOOKS) {
            hooks = (int) HOOKS.stream().filter(hook -> name.isEmpty() || hook.script.equals(name)).count();
            tasks = (int) TASKS.stream().filter(task -> name.isEmpty() || task.script.equals(name)).count();
        }
        result.addProperty("hooks", hooks);
        result.addProperty("tasks", tasks);
        result.addProperty("directory", directory().toString());
        return result.toString();
    }

    private static String api() {
        JsonArray rows = new JsonArray();
        for (String[] row : CATALOG) {
            JsonObject item = new JsonObject();
            item.addProperty("name", row[0]);
            item.addProperty("args", row[1]);
            item.addProperty("returns", row[2]);
            item.addProperty("example", row[3]);
            rows.add(item);
        }
        JsonArray events = new JsonArray();
        for (String event : List.of("tick", "chat", "world", "enable", "disable")) events.add(event);
        JsonObject result = new JsonObject();
        result.addProperty("version", "2");
        result.add("functions", rows);
        result.add("events", events);
        result.addProperty("lifecycle", "Scripts in the lua directory load automatically at startup, before module config is restored. Each script registers an independent module in the LUA category, initially disabled. Return a table with name plus onTick/onEnable/onDisable/onChat/onWorld, call ardion.on, or define function onEventName. Toggle and bind through the normal module UI. Reload preserves the module's enabled state and bind. A failed load restores the previous hooks and timers. Disable skips hooks and timers.");
        result.addProperty("eventObjects", "Hooks named by the event class, simple or binary, receive the live Java event. tick, chat and world keep their old payloads. Fields and methods on that object are readable and writable, including non-public.");
        result.addProperty("errors", "Load and runtime errors are returned by lua action errors. pcall inside a script isolates that script. A script error does not stop the client.");
        result.addProperty("access", "Full JSE Lua plus luajava and jvm. bindClass accepts yarn or intermediary names and uses the game class loader. Calls and field writes include non-public members. Chunks compile to Java bytecode. io, os and package are loaded. A script can exit the JVM.");
        result.addProperty("repairLimit", REPAIRS);
        return result.toString();
    }

    private static void install(Globals globals) {
        LuaJavaBridge.install(globals);
        String root = directory().toString().replace('\\', '/');
        LuaValue pkg = globals.get("package");
        pkg.set("path", root + "/?.lua;" + root + "/?/init.lua;" + pkg.get("path").tojstring());
        globals.set("jvm", globals.get("luajava"));
        LuaTable ardion = new LuaTable();
        ardion.set("on", fn((args) -> { on(str(args, 1), args.arg(2)); return LuaValue.NIL; }));
        ardion.set("after", fn((args) -> { after(args.arg(1).checkint(), args.arg(2).checkfunction()); return LuaValue.NIL; }));
        ardion.set("log", fn((args) -> { remember(owner(), text(args)); return LuaValue.NIL; }));
        ardion.set("notify", fn((args) -> { AgentNotify.post(str(args, 1), text(args, 2)); return LuaValue.NIL; }));
        ardion.set("client", fn((args) -> clientValue()));
        ardion.set("player", fn((args) -> player()));
        ardion.set("modules", fn((args) -> moduleList()));
        ardion.set("module_set", fn((args) -> { moduleSet(str(args, 1), args.arg(2).checkboolean()); return LuaValue.TRUE; }));
        ardion.set("setting", fn((args) -> { setting(str(args, 1), str(args, 2), str(args, 3)); return LuaValue.TRUE; }));
        ardion.set("command", fn((args) -> LuaValue.valueOf(command(str(args, 1)))));
        ardion.set("say", fn((args) -> { say(str(args, 1)); return LuaValue.TRUE; }));
        ardion.set("require", fn((args) -> { require(str(args, 1)); return LuaValue.TRUE; }));
        ardion.set("version", LuaValue.valueOf("2"));
        globals.set("ardion", ardion);
        globals.set("print", fn((args) -> { remember(owner(), text(args)); return LuaValue.NIL; }));
    }

    private static LuaValue clientValue() {
        MinecraftClient client = client();
        if (client == null) return LuaValue.NIL;
        if (!client.isOnThread()) throw new LuaError("ardion.client must run on the client thread");
        return LuaJavaBridge.toLua(client);
    }

    private static LuaValue player() {
        MinecraftClient client = client();
        if (client == null || client.player == null) return LuaValue.NIL;
        if (!client.isOnThread()) throw new LuaError("ardion.player must run on the client thread");
        LuaTable row = LuaValue.tableOf();
        row.set("x", LuaValue.valueOf(client.player.getX()));
        row.set("y", LuaValue.valueOf(client.player.getY()));
        row.set("z", LuaValue.valueOf(client.player.getZ()));
        row.set("yaw", LuaValue.valueOf(client.player.getYaw()));
        row.set("pitch", LuaValue.valueOf(client.player.getPitch()));
        row.set("health", LuaValue.valueOf(client.player.getHealth()));
        row.set("name", LuaValue.valueOf(client.player.getName().getString()));
        return row;
    }

    private static LuaValue moduleList() throws Exception {
        JsonObject parsed = com.google.gson.JsonParser.parseString(ArdionTools.modules(new JsonObject())).getAsJsonObject();
        LuaTable rows = LuaValue.tableOf();
        int index = 1;
        for (var element : parsed.getAsJsonArray("modules")) {
            JsonObject module = element.getAsJsonObject();
            LuaTable row = LuaValue.tableOf();
            row.set("name", module.get("name").getAsString());
            row.set("enabled", LuaValue.valueOf(module.get("enabled").getAsBoolean()));
            row.set("category", module.get("category").getAsString());
            rows.set(index++, row);
        }
        return rows;
    }

    private static void moduleSet(String name, boolean enabled) throws Exception {
        if (SCRIPTS.containsKey(name)) {
            setEnabled(name, enabled);
            return;
        }
        if (ModuleManager.getModule(name) instanceof ScriptModule module) {
            setEnabled(module.script, enabled);
            return;
        }
        JsonObject args = new JsonObject();
        args.addProperty("action", "enable");
        args.addProperty("module", name);
        args.addProperty("enabled", enabled);
        ArdionTools.modules(args);
    }

    private static void setting(String module, String setting, String value) throws Exception {
        JsonObject args = new JsonObject();
        args.addProperty("action", "set");
        args.addProperty("module", module);
        args.addProperty("setting", setting);
        args.addProperty("value", value);
        ArdionTools.modules(args);
    }

    private static String command(String command) throws Exception {
        JsonObject args = new JsonObject();
        args.addProperty("action", "execute");
        args.addProperty("command", command);
        return ArdionTools.command(args);
    }

    private static void say(String text) {
        MinecraftClient client = client();
        if (client == null || !client.isOnThread()) throw new LuaError("ardion.say must run on the client thread");
        GameChatTools.chatSend(text);
    }

    private static void require(String name) throws Exception {
        String file = scriptName(name);
        if (SCRIPTS.containsKey(file)) return;
        load(file);
    }

    private static void on(String event, LuaValue fn) {
        if (event.isBlank()) throw new LuaError("Unknown event");
        add(owner(), event, fn);
    }

    private static Map<String, LuaValue> onFunctions(Globals state) {
        Map<String, LuaValue> found = new HashMap<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = state.next(key);
            key = next.arg1();
            if (key.isnil()) return found;
            if (!key.isstring() || !next.arg(2).isfunction()) continue;
            String name = key.tojstring();
            if (name.startsWith("on") && name.length() > 2) found.put(name, next.arg(2));
        }
    }

    private static String eventName(String function) {
        String raw = function.substring(2);
        return switch (raw) {
            case "Tick" -> "tick";
            case "Enable" -> "enable";
            case "Disable" -> "disable";
            case "Chat" -> "chat";
            case "World" -> "world";
            default -> raw;
        };
    }

    private static void after(int ticks, LuaValue fn) {
        if (ticks < 1 || ticks > 12000) throw new LuaError("ticks must be 1-12000");
        Task task = new Task();
        task.script = owner();
        task.left = ticks;
        task.fn = fn;
        synchronized (HOOKS) { TASKS.add(task); }
    }

    private static void add(String script, String event, LuaValue fn) {
        if (fn == null || !fn.isfunction()) return;
        if (script == null) throw new LuaError("Register hooks while the script is loading");
        Hook hook = new Hook();
        hook.script = script;
        hook.event = event;
        hook.fn = fn;
        if (!plain(event)) hook.type = eventClass(event);
        synchronized (HOOKS) { HOOKS.add(hook); }
        refreshWatched();
    }

    private static boolean plain(String event) {
        return switch (event) {
            case "tick", "chat", "world", "enable", "disable" -> true;
            default -> false;
        };
    }

    private static Class<?> eventClass(String name) {
        ClassLoader loader = LuaRuntime.class.getClassLoader();
        try {
            if (name.indexOf('.') >= 0) return eventType(Class.forName(name, false, loader));
            for (String pkg : EVENT_PACKAGES) {
                try {
                    Class<?> type = eventType(Class.forName("io.github.wzhy233.ardion.event.impl." + pkg + "." + name, false, loader));
                    if (type != null) return type;
                } catch (ClassNotFoundException ignored) {
                }
            }
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    private static Class<?> eventType(Class<?> type) {
        return Event.class.isAssignableFrom(type) ? type : null;
    }

    private static void refreshWatched() {
        Set<Class<?>> next = new HashSet<>();
        synchronized (HOOKS) {
            for (Hook hook : HOOKS) if (hook.type != null) next.add(hook.type);
        }
        watched = next.isEmpty() ? null : Set.copyOf(next);
    }

    private static void runTasks() {
        List<Task> due = new ArrayList<>();
        synchronized (HOOKS) {
            var iterator = TASKS.iterator();
            while (iterator.hasNext()) {
                Task task = iterator.next();
                if (!enabled(task.script)) continue;
                if (--task.left <= 0) { iterator.remove(); due.add(task); }
            }
        }
        for (Task task : due) callHook(task.script, task.fn, LuaValue.NIL);
    }

    private static void callHook(String script, LuaValue fn, LuaValue arg) {
        String previous = OWNER.get();
        OWNER.set(script);
        try { fn.call(arg); }
        catch (Throwable error) { remember(script, error); }
        finally { if (previous == null) OWNER.remove(); else OWNER.set(previous); }
    }

    private static void failed(String name, Throwable error) {
        FAILURES.merge(name, 1, Integer::sum);
        remember(name, error);
    }

    private static void remember(String script, Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        remember(script, trace.toString());
        io.github.wzhy233.ardion.Ardion.LOGGER.error("Lua script failed: " + script, error);
    }

    private static void remember(String script, String message) {
        String line = (script == null ? "?" : script) + ": " + message;
        synchronized (ERRORS) {
            ERRORS.addLast(line);
            while (ERRORS.size() > 20) ERRORS.removeFirst();
        }
    }

    private static void ensureListener() {
        if (listening || client() == null) return;
        listening = true;
        EventManager.register(new Bus());
    }

    private static Globals globals() {
        if (globals == null) {
            Globals created = new Globals();
            install(created);
            globals = created;
        }
        return globals;
    }

    private static String owner() {
        String owner = OWNER.get();
        if (owner == null) throw new LuaError("ardion calls that register state must run during script load");
        return owner;
    }

    static Path directory() {
        String override = System.getProperty("ardion.lua.dir");
        Path dir = override == null || override.isBlank() ? ArdionHome.root().resolve("lua") : Path.of(override);
        return dir.toAbsolutePath().normalize();
    }

    private static Path file(String name) {
        Path dir = directory();
        Path file = dir.resolve(name + ".lua").normalize();
        if (!file.startsWith(dir)) throw new IllegalArgumentException("Script path escaped the lua directory");
        return file;
    }

    private static String script(JsonObject args) {
        return scriptName(ArdionTools.required(args, "name"));
    }

    private static String scriptName(String raw) {
        String name = raw.endsWith(".lua") ? raw.substring(0, raw.length() - 4) : raw;
        if (!name.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) throw new IllegalArgumentException("Script name must match [A-Za-z][A-Za-z0-9_-]{0,63}");
        return name;
    }

    private static String source(JsonObject args) {
        if (!args.has("source") || !args.get("source").isJsonPrimitive()) throw new IllegalArgumentException("source is required");
        return args.get("source").getAsString();
    }

    private static boolean force(JsonObject args) {
        return args.has("force") && args.get("force").isJsonPrimitive() && args.get("force").getAsBoolean();
    }

    private static MinecraftClient client() {
        try { return MinecraftClient.getInstance(); }
        catch (Throwable ignored) { return null; }
    }

    private static VarArgFunction fn(Checked fn) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                try { return fn.apply(args); }
                catch (LuaError error) { throw error; }
                catch (Exception error) { throw new LuaError(error); }
            }
        };
    }

    private static String str(Varargs args, int index) {
        return args.arg(index).checkjstring();
    }

    private static String text(Varargs args) {
        return text(args, 1);
    }

    private static String text(Varargs args, int from) {
        StringBuilder line = new StringBuilder();
        for (int i = from; i <= args.narg(); i++) {
            if (i > from) line.append('\t');
            line.append(args.arg(i).tojstring());
        }
        return line.toString();
    }

    @FunctionalInterface
    private interface Checked { Varargs apply(Varargs args) throws Exception; }

    private static void lifecycle(String script, boolean on, List<Hook> hooks) {
        for (Hook hook : hooks) {
            if (hook.script.equals(script) && hook.event.equals(on ? "enable" : "disable"))
                callHook(script, hook.fn, LuaValue.NIL);
        }
    }

    private static final class ScriptModule extends Module {
        private final String script;

        ScriptModule(String script, String name) {
            super(name, ModuleCategory.LUA);
            this.script = script;
        }

        @Override protected void onEnable() { runLifecycle(true); }
        @Override protected void onDisable() { runLifecycle(false); }

        private void runLifecycle(boolean on) {
            LOCK.lock();
            try {
                if (SCRIPTS.get(script) == this) lifecycle(script, on, List.copyOf(HOOKS));
            } finally { LOCK.unlock(); }
        }
    }

    private static final class Hook {
        String script, event;
        Class<?> type;
        LuaValue fn;
    }

    private static final class Task {
        String script;
        int left;
        LuaValue fn;
    }

    public static final class Bus {
        @Listener public void tick(MinecraftTickEvent event) {
            if (event.getStage() != MinecraftTickEvent.Stage.PRE || SCRIPTS.isEmpty() || !LOCK.tryLock()) return;
            try {
                dispatchLocked("tick", LuaValue.NIL);
                runTasks();
            } finally { LOCK.unlock(); }
        }

        @Listener public void chat(ChatReceiveEvent event) {
            if (SCRIPTS.isEmpty() || !LOCK.tryLock()) return;
            try { dispatchLocked("chat", LuaValue.valueOf(event.getPlainMessage())); }
            finally { LOCK.unlock(); }
        }

        @Listener public void world(WorldLoadEvent event) {
            if (SCRIPTS.isEmpty() || !LOCK.tryLock()) return;
            try {
                String id = event.getWorld() == null ? "" : String.valueOf(event.getWorld().getRegistryKey().getValue());
                dispatchLocked("world", LuaValue.valueOf(id));
            } finally { LOCK.unlock(); }
        }
    }

    private static final String[][] CATALOG = {
            {"ardion.on", "event or class name, function", "nil", "ardion.on('PostJumpEvent', function() ardion.say('I am jumping') end)"},
            {"ardion.client", "", "MinecraftClient or nil", "local player = ardion.client().player"},
            {"luajava.bindClass", "yarn, intermediary or binary name", "java class", "local mc = luajava.bindClass('net.minecraft.client.MinecraftClient')"},
            {"jvm", "alias of luajava", "table", "jvm.bindClass('java.lang.System'):currentTimeMillis()"},
            {"onEventName", "Java event", "", "function onChatReceiveEvent(e) ardion.log(e:getPlainMessage()) end"},
            {"ardion.after", "ticks, function", "nil", "ardion.after(20, function() ardion.log('later') end)"},
            {"ardion.player", "", "table or nil", "local p = ardion.player()"},
            {"ardion.modules", "", "array of {name,enabled,category}", "for _, m in ipairs(ardion.modules()) do ardion.log(m.name) end"},
            {"ardion.module_set", "name, enabled", "true", "ardion.module_set('hud', true)"},
            {"ardion.setting", "module, setting, value", "true", "ardion.setting('hud', 'some', '1')"},
            {"ardion.command", "client command without slash", "result json", "ardion.command('help')"},
            {"ardion.say", "chat text", "true", "ardion.say('hello')"},
            {"ardion.notify", "level, text", "nil", "ardion.notify('info', 'ready')"},
            {"ardion.log", "text", "nil", "ardion.log('loaded')"},
            {"ardion.require", "script name", "true", "ardion.require('lib')"},
            {"return table", "name, onTick, onEnable, onDisable, onChat, onWorld", "module", "return { name = 'demo', onTick = function() end }"}
    };
}
