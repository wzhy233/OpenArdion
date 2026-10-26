package io.github.wzhy233.ardion.irc;

import io.github.wzhy233.ardion.setting.ProxySetting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public final class IrcProxy {
    private static volatile BooleanSupplier useProxy = () -> false;

    private IrcProxy() {
    }

    public static void watch(BooleanSupplier supplier) {
        useProxy = supplier == null ? () -> false : supplier;
    }

    public static boolean enabled() {
        return useProxy.getAsBoolean();
    }

    public static Socket connect(String targetHost, int targetPort, int timeoutMs) throws IOException {
        return open(ProxySetting.type(), ProxySetting.host(), ProxySetting.port(),
                ProxySetting.username(), ProxySetting.password(), targetHost, targetPort, timeoutMs);
    }

    public static Socket open(String type, String proxyHost, int proxyPort, String username, String password,
                               String targetHost, int targetPort, int timeoutMs) throws IOException {
        if (proxyHost == null || proxyHost.isBlank()) throw new IOException("IRC proxy host is empty");
        if (targetHost == null || targetHost.isBlank()) throw new IOException("IRC proxy target is empty");
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(proxyHost.trim(), proxyPort), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
                case "http" -> http(socket, targetHost, targetPort, username, password);
                case "socket", "socks5" -> socks5(socket, targetHost, targetPort, username, password);
                case "socks4" -> socks4(socket, targetHost, targetPort, username);
                default -> throw new IOException("Unknown IRC proxy type: " + type);
            }
            return socket;
        } catch (IOException exception) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            throw exception;
        }
    }

    private static void http(Socket socket, String host, int port, String username, String password) throws IOException {
        rejectBreaks(host, "host");
        rejectBreaks(username, "username");
        rejectBreaks(password, "password");
        StringBuilder request = new StringBuilder();
        request.append("CONNECT ").append(host).append(':').append(port).append(" HTTP/1.1\r\n");
        request.append("Host: ").append(host).append(':').append(port).append("\r\n");
        if (username != null && !username.isEmpty()) {
            String token = Base64.getEncoder().encodeToString(
                    (username + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8));
            request.append("Proxy-Authorization: Basic ").append(token).append("\r\n");
        }
        request.append("\r\n");
        OutputStream out = socket.getOutputStream();
        out.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        String status = readHttpStatus(socket.getInputStream());
        if (status.length() < 12 || !status.startsWith("HTTP/1.") || !"200".equals(status.substring(9, 12))) {
            throw new IOException("IRC HTTP proxy rejected CONNECT: " + status);
        }
    }

    private static void socks5(Socket socket, String host, int port, String username, String password) throws IOException {
        boolean auth = username != null && !username.isEmpty();
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        out.write(auth ? new byte[]{5, 1, 2} : new byte[]{5, 1, 0});
        out.flush();
        byte[] greeting = readExact(in, 2);
        if (greeting[0] != 5) throw new IOException("IRC SOCKS5 proxy version mismatch");
        if (auth) {
            if (greeting[1] != 2) throw new IOException("IRC SOCKS5 proxy refused username auth");
            byte[] user = username.getBytes(StandardCharsets.UTF_8);
            byte[] pass = (password == null ? "" : password).getBytes(StandardCharsets.UTF_8);
            if (user.length > 255 || pass.length > 255) throw new IOException("IRC SOCKS5 credentials are too long");
            byte[] login = new byte[3 + user.length + pass.length];
            login[0] = 1;
            login[1] = (byte) user.length;
            System.arraycopy(user, 0, login, 2, user.length);
            login[2 + user.length] = (byte) pass.length;
            System.arraycopy(pass, 0, login, 3 + user.length, pass.length);
            out.write(login);
            out.flush();
            if (readExact(in, 2)[1] != 0) throw new IOException("IRC SOCKS5 authentication failed");
        } else if (greeting[1] != 0) {
            throw new IOException("IRC SOCKS5 proxy requires authentication");
        }
        byte[] name = host.getBytes(StandardCharsets.UTF_8);
        if (name.length == 0 || name.length > 255) throw new IOException("IRC proxy target host is invalid");
        byte[] request = new byte[7 + name.length];
        request[0] = 5;
        request[1] = 1;
        request[3] = 3;
        request[4] = (byte) name.length;
        System.arraycopy(name, 0, request, 5, name.length);
        request[5 + name.length] = (byte) (port >> 8);
        request[6 + name.length] = (byte) port;
        out.write(request);
        out.flush();
        byte[] head = readExact(in, 4);
        if (head[1] != 0) throw new IOException("IRC SOCKS5 connect failed: " + (head[1] & 0xff));
        int address = switch (head[3] & 0xff) {
            case 1 -> 4;
            case 4 -> 16;
            case 3 -> readExact(in, 1)[0] & 0xff;
            default -> throw new IOException("IRC SOCKS5 proxy returned an unknown address");
        };
        readExact(in, address + 2);
    }

    private static void socks4(Socket socket, String host, int port, String username) throws IOException {
        byte[] user = (username == null ? "" : username).getBytes(StandardCharsets.UTF_8);
        byte[] ip = ipv4(host);
        byte[] name = ip == null ? host.getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (ip == null && (name.length == 0 || name.length > 255)) throw new IOException("IRC proxy target host is invalid");
        int size = ip == null ? 1 + user.length + name.length : user.length;
        byte[] request = new byte[9 + size];
        request[0] = 4;
        request[1] = 1;
        request[2] = (byte) (port >> 8);
        request[3] = (byte) port;
        if (ip == null) request[7] = 1;
        else System.arraycopy(ip, 0, request, 4, 4);
        System.arraycopy(user, 0, request, 8, user.length);
        if (ip == null) System.arraycopy(name, 0, request, 9 + user.length, name.length);
        OutputStream out = socket.getOutputStream();
        out.write(request);
        out.flush();
        byte[] reply = readExact(socket.getInputStream(), 8);
        if ((reply[1] & 0xff) != 90) throw new IOException("IRC SOCKS4 connect failed: " + (reply[1] & 0xff));
    }

    private static byte[] ipv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return null;
        byte[] ip = new byte[4];
        for (int i = 0; i < 4; i++) {
            int value;
            try {
                value = Integer.parseInt(parts[i]);
            } catch (NumberFormatException exception) {
                return null;
            }
            if (value < 0 || value > 255 || !Integer.toString(value).equals(parts[i])) return null;
            ip[i] = (byte) value;
        }
        return ip;
    }

    private static String readHttpStatus(InputStream in) throws IOException {
        String status = readLine(in);
        if (status.isEmpty()) throw new IOException("IRC HTTP proxy returned an empty response");
        for (int i = 0; i < 64; i++) {
            if (readLine(in).isEmpty()) return status;
        }
        throw new IOException("IRC HTTP proxy header is too large");
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        while (line.length() < 8192) {
            int value = in.read();
            if (value < 0) throw new IOException("IRC proxy closed");
            if (value == '\n') {
                if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') line.setLength(line.length() - 1);
                return line.toString();
            }
            line.append((char) value);
        }
        throw new IOException("IRC HTTP proxy line is too large");
    }

    private static byte[] readExact(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read < 0) throw new IOException("IRC proxy closed");
            offset += read;
        }
        return buffer;
    }

    private static void rejectBreaks(String value, String label) throws IOException {
        if (value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)) {
            throw new IOException("IRC proxy " + label + " is invalid");
        }
    }
}
