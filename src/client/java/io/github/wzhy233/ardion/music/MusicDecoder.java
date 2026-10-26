package io.github.wzhy233.ardion.music;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.advanced.AdvancedPlayer;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

final class MusicDecoder extends AdvancedPlayer {
    private final Executor closer;
    private final BooleanSupplier current;
    private final CloseOnceDevice device;
    private final AtomicBoolean closing = new AtomicBoolean();

    MusicDecoder(InputStream input, AudioDevice device, BooleanSupplier current, Executor closer) throws JavaLayerException {
        this(input, new CloseOnceDevice(device), current, closer);
    }

    private MusicDecoder(InputStream input, CloseOnceDevice device, BooleanSupplier current, Executor closer) throws JavaLayerException {
        super(input, device);
        this.device = device;
        this.current = current;
        this.closer = closer;
    }

    @Override protected boolean decodeFrame() throws JavaLayerException {
        return current.getAsBoolean() && super.decodeFrame();
    }

    @Override protected boolean skipFrame() throws JavaLayerException {
        return current.getAsBoolean() && super.skipFrame();
    }

    @Override public void close() {
        if (closing.compareAndSet(false, true)) closer.execute(() -> {
            try { device.close(); }
            finally { super.close(); }
        });
    }

    private static final class CloseOnceDevice implements AudioDevice {
        private final AudioDevice delegate;
        private final AtomicBoolean closing = new AtomicBoolean();
        CloseOnceDevice(AudioDevice delegate) { this.delegate = delegate; }
        @Override public void open(Decoder decoder) throws JavaLayerException { delegate.open(decoder); }
        @Override public boolean isOpen() { return !closing.get() && delegate.isOpen(); }
        @Override public void write(short[] samples, int offset, int length) throws JavaLayerException {
            if (!closing.get()) delegate.write(samples, offset, length);
        }
        @Override public void flush() { if (!closing.get()) delegate.flush(); }
        @Override public int getPosition() { return delegate.getPosition(); }
        @Override public void close() { if (closing.compareAndSet(false, true)) delegate.close(); }
    }
}
