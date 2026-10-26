package io.github.wzhy233.ardion.module.category;

import io.github.wzhy233.ardion.module.base.Module;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public enum ModuleCategory {
    COMBAT,
    MOVEMENT,
    PLAYER,
    WORLD,
    RENDER,
    CLIENT,
    MISC,
    LUA;

    private final Set<Module> pinnedModules = ConcurrentHashMap.newKeySet();

    public List<Module> getPinnedModules() {
        return new ArrayList<>(pinnedModules);
    }

    public boolean isPinned(Module module) {
        return pinnedModules.contains(module);
    }

    public void togglePinned(Module module) {
        if (!pinnedModules.remove(module)) {
            pinnedModules.add(module);
        }
    }
}
