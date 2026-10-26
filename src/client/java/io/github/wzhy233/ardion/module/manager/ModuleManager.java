package io.github.wzhy233.ardion.module.manager;

import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.impl.combat.KillAura;
import io.github.wzhy233.ardion.module.impl.combat.Criticals;
import io.github.wzhy233.ardion.module.impl.combat.MoreKB;
import io.github.wzhy233.ardion.module.impl.combat.TriggerBot;
import io.github.wzhy233.ardion.module.impl.combat.Velocity;
import io.github.wzhy233.ardion.module.impl.misc.AntiBot;
import io.github.wzhy233.ardion.module.impl.misc.MCF;
import io.github.wzhy233.ardion.module.impl.misc.IRC;
import io.github.wzhy233.ardion.module.impl.misc.Disabler;
import io.github.wzhy233.ardion.module.impl.misc.NoteBot;
import io.github.wzhy233.ardion.module.impl.misc.Spoofer;
import io.github.wzhy233.ardion.module.impl.movement.*;
import io.github.wzhy233.ardion.module.impl.player.*;
import io.github.wzhy233.ardion.module.impl.render.*;
import io.github.wzhy233.ardion.module.impl.world.BedNuker;
import io.github.wzhy233.ardion.module.impl.world.Scaffold;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public final class ModuleManager {
    private static final List<Module> modules = new ArrayList<>();
    private static final List<Module> modulesView = Collections.unmodifiableList(modules);
    private static final Map<String, Module> modulesByName = new HashMap<>();
    private static final Map<Class<? extends Module>, Module> modulesByClass = new HashMap<>();
    private static final Map<ModuleCategory, List<Module>> modulesByCategory = new EnumMap<>(ModuleCategory.class);
    private static final Map<ModuleCategory, List<Module>> modulesByCategoryView = new EnumMap<>(ModuleCategory.class);
    private static final Map<Integer, List<Module>> modulesByBind = new HashMap<>();
    private static final Map<Integer, List<Module>> modulesByBindView = new HashMap<>();

    private ModuleManager() {
    }

    public static void init() {
        modules.clear();
        modulesByName.clear();
        modulesByClass.clear();
        modulesByCategory.clear();
        modulesByCategoryView.clear();
        modulesByBind.clear();
        modulesByBindView.clear();
        register(new Sprint());
        register(new GUIMove());
        register(new BridgeAssist());
        register(new NoJumpDelay());
        register(new JumpReset());
        register(new FastWeb());
        register(new KeepSprint());
        register(new NoSlow());
        register(new AutoTotem());
        register(new AutoMLG());
        register(new AutoTool());
        register(new InvManager());
        register(new ChestStealer());
        register(new FastPlace());
        register(new Scaffold());
        register(new BedNuker());
        register(new AutoGG());
        register(new FreeLook());
        register(new KillAura());
        register(new TriggerBot());
        register(new MoreKB());
        register(new Velocity());
        register(new Criticals());
        register(new ClickGUI());
        register(new ESP());
        register(new BedESP());
        register(new ContainerESP());
        register(new Xray());
        register(new PigGod());
        register(new AntiBot());
        register(new MCF());
        register(new IRC());
        register(new Disabler());
        register(new Spoofer());
        register(new HideScoreBoard());
        register(new HUD());
        register(new MusicPlayer());
        register(new OOTD());
        register(new CustomSkin());
        register(new GlowESP());
        register(new FullBright());
        register(new WeatherChanger());
        register(new AntiBlind());
        register(new NoRender());
        register(new Rotations());
        register(new NoteBot());
        register(new BetterTab());
        register(new ProjectileTrail());
        register(new KillEffect());
        register(new BetterTooltips());
        register(new ViewClip());
    }

    public static void register(Module module) {
        if (!module.isActive()) return;
        modules.add(module);
        modulesByName.put(normalize(module.getName()), module);
        modulesByClass.put(module.getClass(), module);
        categoryModules(module.getCategory()).add(module);
        if (module.hasBind()) {
            bindModules(module.getBindKey()).add(module);
        }
    }

    public static List<Module> getModules() {
        return modulesView;
    }

    public static void unregister(Module module) {
        if (!modules.contains(module)) return;
        module.setEnabledSilently(false);
        updateBind(module, module.getBindKey(), GLFW.GLFW_KEY_UNKNOWN);
        modules.remove(module);
        modulesByName.remove(normalize(module.getName()), module);
        modulesByClass.remove(module.getClass(), module);
        for (Module remaining : modules) {
            if (remaining.getClass() == module.getClass()) modulesByClass.put(module.getClass(), remaining);
        }
        categoryModules(module.getCategory()).remove(module);
        if (module.getCategory().isPinned(module)) module.getCategory().togglePinned(module);
    }

    public static List<Module> getModulesByCategory(ModuleCategory category) {
        return modulesByCategoryView.getOrDefault(category, List.of());
    }

    public static Module getModule(String name) {
        return name == null ? null : modulesByName.get(normalize(name));
    }

    public static List<Module> getModulesByBind(int bindKey) {
        return modulesByBindView.getOrDefault(bindKey, List.of());
    }

    public static <T extends Module> T getModule(Class<T> moduleClass) {
        Module exact = modulesByClass.get(moduleClass);
        if (exact != null) {
            return moduleClass.cast(exact);
        }

        for (Module module : modules) {
            if (moduleClass.isInstance(module)) {
                return moduleClass.cast(module);
            }
        }
        return null;
    }

    public static void updateBind(Module module, int oldBind, int newBind) {
        if (!modules.contains(module)) {
            return;
        }

        if (oldBind == newBind) {
            return;
        }

        if (oldBind != GLFW.GLFW_KEY_UNKNOWN) {
            List<Module> oldModules = modulesByBind.get(oldBind);
            if (oldModules != null) {
                oldModules.remove(module);
                if (oldModules.isEmpty()) {
                    modulesByBind.remove(oldBind);
                    modulesByBindView.remove(oldBind);
                }
            }
        }

        if (newBind != GLFW.GLFW_KEY_UNKNOWN) {
            bindModules(newBind).add(module);
        }
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static List<Module> categoryModules(ModuleCategory category) {
        List<Module> categoryModules = modulesByCategory.get(category);
        if (categoryModules == null) {
            categoryModules = new ArrayList<>();
            modulesByCategory.put(category, categoryModules);
            modulesByCategoryView.put(category, Collections.unmodifiableList(categoryModules));
        }
        return categoryModules;
    }

    private static List<Module> bindModules(int bindKey) {
        List<Module> boundModules = modulesByBind.get(bindKey);
        if (boundModules == null) {
            boundModules = new ArrayList<>();
            modulesByBind.put(bindKey, boundModules);
            modulesByBindView.put(bindKey, Collections.unmodifiableList(boundModules));
        }
        return boundModules;
    }
}
