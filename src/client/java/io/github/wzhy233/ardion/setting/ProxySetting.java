package io.github.wzhy233.ardion.setting;

import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.base.Value;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import io.github.wzhy233.ardion.value.impl.StringValue;

import java.util.List;

public final class ProxySetting {
    private static final SettingOwner OWNER = new SettingOwner();
    private static final ModeValue type = new ModeValue("proxy-type", OWNER, "socket",
            "http", "socket", "socks5", "socks4");
    private static final StringValue host = new StringValue("proxy-host", OWNER, "127.0.0.1");
    private static final NumberValue port = new NumberValue("proxy-port", OWNER, 1080, 1, 65535, true);
    private static final StringValue username = new StringValue("proxy-username", OWNER, "");
    private static final StringValue password = new StringValue("proxy-password", OWNER, "");
    private static final List<Value<?>> values = List.of(type, host, port, username, password);

    private ProxySetting() {
    }

    public static List<Value<?>> getValues() {
        return values;
    }

    public static String type() {
        return type.getValue();
    }

    public static String host() {
        return host.getValue() == null ? "" : host.getValue().trim();
    }

    public static int port() {
        return port.getValue().intValue();
    }

    public static String username() {
        return username.getValue() == null ? "" : username.getValue();
    }

    public static String password() {
        return password.getValue() == null ? "" : password.getValue();
    }

    public static String key() {
        return type() + "\n" + host() + "\n" + port() + "\n" + username() + "\n" + password();
    }

    private static final class SettingOwner extends Module {
        private SettingOwner() {
            super("proxy", ModuleCategory.CLIENT);
        }
    }
}
