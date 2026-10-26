package io.github.wzhy233.ardion.music;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MusicHttp {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ScheduledThreadPoolExecutor TIMEOUTS = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "Ardion-Music-HttpTimeout");
        thread.setDaemon(true);
        return thread;
    });
    static { TIMEOUTS.setRemoveOnCancelPolicy(true); }

    private MusicHttp() {}

    public record Response(int statusCode, HttpHeaders headers, InputStream body) implements AutoCloseable {
        @Override public void close() throws IOException { body.close(); }
    }

    public static Response send(HttpRequest request, Duration readTimeout) throws IOException {
        try {
            var response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            return new Response(response.statusCode(), response.headers(),
                    new TimedInput(response.body(), readTimeout.toMillis()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Music request cancelled");
        }
    }

    private static final class TimedInput extends FilterInputStream {
        private final long timeoutMs;
        TimedInput(InputStream input, long timeoutMs) { super(input); this.timeoutMs = timeoutMs; }

        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 255;
        }

        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Music read cancelled");
            if (length == 0) return 0;
            AtomicBoolean pending = new AtomicBoolean(true);
            AtomicBoolean expired = new AtomicBoolean();
            var timeout = TIMEOUTS.schedule(() -> {
                if (pending.compareAndSet(true, false)) {
                    expired.set(true);
                    try { in.close(); } catch (IOException ignored) {}
                }
            }, timeoutMs, TimeUnit.MILLISECONDS);
            try {
                int count = in.read(bytes, offset, length);
                if (expired.get()) throw new SocketTimeoutException("Music response read timed out");
                return count;
            } catch (IOException failure) {
                if (expired.get()) throw new SocketTimeoutException("Music response read timed out");
                throw failure;
            } finally {
                pending.set(false);
                timeout.cancel(false);
            }
        }
    }
}
