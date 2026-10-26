package io.github.humbleui.skija.impl;

import java.lang.ref.Cleaner;

public final class Cleanable {
    private static final Cleaner CLEANER = Cleaner.create();

    private final Cleaner.Cleanable cleanable;

    public static Cleanable register(Object obj, Runnable action) {
        return new Cleanable(CLEANER.register(obj, action));
    }

    private Cleanable(Cleaner.Cleanable cleanable) {
        this.cleanable = cleanable;
    }

    public void clean() {
        cleanable.clean();
    }
}
