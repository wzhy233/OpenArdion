package io.github.wzhy233.ardion.util.server;

public enum Server {
    HYPIXEL("hypixel"),
    MINEMEN("minemen");

    private final String addressFragment;

    Server(String addressFragment) {
        this.addressFragment = addressFragment;
    }

    public String getAddressFragment() {
        return this.addressFragment;
    }
}
