package io.github.wzhy233.ardion.ai;

import io.github.wzhy233.ardion.mixin.render.ChatHudAccessor;
import io.github.wzhy233.ardion.setting.GlobalSetting;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class AiService {
    private static final AtomicReference<ChatHudLine> PINNED = new AtomicReference<>();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final ThreadLocal<Boolean> OWN_MESSAGE = ThreadLocal.withInitial(() -> false);
    private static volatile int streamId;
    private static volatile boolean stopRequested;
    private static volatile Thread requestThread;
    private static volatile StreamOutput activeOutput;
    private static final ThreadPoolExecutor REQUESTS = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2), task -> {
                Thread thread = new Thread(task, "ardion-ai");
                thread.setDaemon(true);
                return thread;
            });

    private AiService() {
    }

    public static boolean interceptChat(String message) {
        if (!GlobalSetting.getChatChannel().is("ai") || message == null || message.isBlank()
                || message.startsWith("/") || message.startsWith("$")) return false;
        ask(message);
        return true;
    }

    public static void ask(String input) {
        String prompt = input == null ? "" : input.trim();
        if (prompt.isEmpty()) return;
        if (!startRequest(prompt)) {
            show("Wait for the current AI reply", Formatting.RED);
        }
    }

    private static boolean startRequest(String prompt) {
        if (!BUSY.compareAndSet(false, true)) return false;
        stopRequested = false;
        try {
            REQUESTS.execute(() -> {
                requestThread = Thread.currentThread();
                try {
                    if (!stopRequested) request(prompt);
                } finally {
                    requestThread = null;
                    MinecraftClient.getInstance().execute(() -> BUSY.set(false));
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            BUSY.set(false);
            return false;
        }
    }

    public static void holdBottom(ChatHud hud) {
        if (OWN_MESSAGE.get()) return;
        ChatHudLine line = PINNED.get();
        if (line == null || hud == null) return;
        ChatHudAccessor accessor = (ChatHudAccessor) hud;
        int index = identity(accessor.ardion$getMessages(), line);
        if (index <= 0) return;
        replaceLine(accessor, line, line);
    }

    private static int visibleStart(List<ChatHudLine.Visible> visible, int messageIndex) {
        int start = 0;
        for (int message = 0; message < messageIndex && start < visible.size(); message++) {
            start++;
            while (start < visible.size() && !visible.get(start).endOfEntry()) start++;
        }
        return start;
    }

    private static void replaceLine(ChatHudAccessor accessor, ChatHudLine previous, ChatHudLine updated) {
        List<ChatHudLine> messages = accessor.ardion$getMessages();
        int index = identity(messages, previous);
        if (index < 0) return;
        List<ChatHudLine.Visible> visible = accessor.ardion$getVisibleMessages();
        int start = visibleStart(visible, index);
        int end = visibleStart(visible, index + 1);
        int scroll = accessor.ardion$getScrolledLines();
        boolean unread = accessor.ardion$hasUnreadNewMessages();
        ChatHudLine.Visible anchor = scroll > 0 && scroll < visible.size() ? visible.get(scroll) : null;
        messages.remove(index);
        messages.addFirst(updated);
        visible.subList(start, end).clear();
        int remaining = visible.size();
        accessor.ardion$setScrolledLines(0);
        accessor.ardion$addVisibleMessage(updated);
        int growth = visible.size() - remaining - (end - start);
        if (scroll > 0) {
            int anchorIndex = -1;
            for (int i = 0; i < visible.size(); i++) {
                if (visible.get(i) == anchor) { anchorIndex = i; break; }
            }
            scroll = anchorIndex >= 0 ? anchorIndex : scroll - start + growth;
        }
        int maxScroll = Math.max(0, visible.size() - accessor.ardion$getVisibleLineCount());
        accessor.ardion$setScrolledLines(Math.max(0, Math.min(scroll, maxScroll)));
        accessor.ardion$setUnreadNewMessages(unread && accessor.ardion$getScrolledLines() > 0);
    }

    private static void request(String prompt) {
        StreamOutput output = new StreamOutput(++streamId);
        activeOutput = output;
        output.start();
        io.github.wzhy233.mcp.AgentNotify.post("info", "Agent started");
        try {
            if (stopRequested) throw new InterruptedException("AI Chat stopped");
            io.github.wzhy233.mcp.AgentChat.run(prompt, output::acceptThinking, output::acceptAnswer, output::waiting, output::acceptTools);
            output.finish();
            io.github.wzhy233.mcp.AgentNotify.post("success", "Agent finished");
        } catch (Exception exception) {
            if (stopRequested || exception instanceof InterruptedException) { output.stopped(); return; }
            output.cancel();
            String detail = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            io.github.wzhy233.mcp.AgentNotify.post("error", "Agent failed: " + detail);
            show("Agent failed: " + detail, Formatting.RED);
        } finally {
            activeOutput = null;
        }
    }

    public static void cancel() {
        if (!BUSY.get()) return;
        stopRequested = true;
        StreamOutput output = activeOutput;
        if (output != null) output.stopped();
        Thread thread = requestThread;
        if (thread != null) thread.interrupt();
        io.github.wzhy233.mcp.AgentChat.stop();
    }

    private static int identity(List<ChatHudLine> messages, ChatHudLine line) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) == line) return i;
        }
        return -1;
    }

    private record Piece(String kind, String text) {
    }

    private record View(boolean answering, String text, String thought, String thoughtFor, List<Piece> pieces) {
    }

    private static void show(String text, Formatting color) {
        MinecraftClient.getInstance().execute(() -> OutputUtil.send(
                Text.literal("[AI] ").formatted(Formatting.LIGHT_PURPLE)
                        .append(Text.literal(text).formatted(color)), false));
    }

    private static final class StreamOutput {
        private final int stream;
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder pending = new StringBuilder();
        private final List<Seg> segments = new ArrayList<>();
        private final AtomicBoolean updateQueued = new AtomicBoolean();
        private final AiChatLine chatLine = new AiChatLine();
        private boolean answering;
        private boolean insideThink;
        private long thinkStart;
        private long thinkEnd;
        private long lastUpdate;
        private volatile boolean closed;
        private volatile boolean waiting = true;

        private static final class Seg {
            private final String kind;
            private String text;

            private Seg(String kind, String text) {
                this.kind = kind;
                this.text = text;
            }
        }

        private StreamOutput(int stream) {
            this.stream = stream;
            chatLine.stream = stream;
        }

        private void start() {
            schedule(true);
            Thread.startVirtualThread(() -> {
                while (!closed) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) { return; }
                    if (!closed && (waiting || thinkingLive())) schedule(true);
                }
            });
        }

        private void waiting() {
            if (closed) return;
            waiting = true;
            schedule(true);
        }

        private static String waitingLabel(long millis) {
            return "Thinking" + new String[] {".  ", ".. ", "...", " ..", "  .", " .."}[(int) ((millis / 300) % 6)];
        }

        private void acceptThinking(String text) {
            if (closed || text == null || text.isBlank()) return;
            waiting = false;
            appendThought(clean(text));
            schedule(false);
        }

        private void acceptTools(String text) {
            if (closed || text == null || text.isBlank()) return;
            waiting = false;
            applyTools(text);
            schedule(false);
        }

        private void acceptAnswer(String text) {
            if (closed || text == null || text.isEmpty()) return;
            waiting = false;
            pending.append(clean(text));
            drain(false);
            schedule(false);
        }

        private String answer() {
            synchronized (content) {
                return content.toString();
            }
        }

        private void finish() {
            drain(true);
            endThought();
            waiting = false;
            closed = true;
            View view = view();
            boolean ready = view.answering() && !view.text().isBlank();
            MinecraftClient.getInstance().execute(() -> {
                if (stream != streamId) return;
                if (ready) chatLine.update(view);
                else chatLine.fail();
                PINNED.set(null);
            });
        }

        private void cancel() {
            closed = true;
            endThought();
            String text = answer();
            if (text.isBlank()) {
                synchronized (content) { content.append("Agent failed"); }
            }
            answering = true;
            View current = view();
            MinecraftClient.getInstance().execute(() -> {
                if (stream != streamId) return;
                chatLine.update(current);
                if (PINNED.get() == chatLine.line) PINNED.set(null);
            });
        }

        private void stopped() {
            closed = true;
            endThought();
            synchronized (content) {
                if (content.isEmpty()) content.append("Stopped");
                else content.append("\nStopped");
            }
            answering = true;
            View current = view();
            MinecraftClient.getInstance().execute(() -> {
                if (stream != streamId) return;
                chatLine.update(current);
                if (PINNED.get() == chatLine.line) PINNED.set(null);
            });
        }

        private View view() {
            synchronized (segments) {
                synchronized (content) {
                    boolean live = thinkStart != 0 && thinkEnd == 0 && !answering && !closed;
                    String label = thoughtLabel();
                    boolean bodies = live && showThinking();
                    List<Piece> pieces = new ArrayList<>();
                    StringBuilder thought = new StringBuilder();
                    if (!live && label != null) pieces.add(new Piece("label", label));
                    for (Seg seg : segments) {
                        if (seg.text.isEmpty()) continue;
                        if (seg.kind.equals("tool")) {
                            if (!answering && !closed) pieces.add(new Piece("tool", seg.text));
                        } else {
                            if (bodies) {
                                thought.append(seg.text);
                                pieces.add(new Piece("thought", seg.text));
                            }
                        }
                    }
                    if (live && label != null) pieces.add(new Piece("label", label));
                    String answerText = content.toString();
                    if (answering && !answerText.isEmpty()) pieces.add(new Piece("answer", answerText));
                    if (waiting && !closed) {
                        String spin = waitingLabel(System.currentTimeMillis());
                        pieces.add(new Piece("wait", spin));
                        if (pieces.size() == 1) return new View(false, spin, "", null, List.copyOf(pieces));
                    }
                    return new View(answering, answering ? answerText : "", thought.toString(), label, List.copyOf(pieces));
                }
            }
        }

        private boolean thinkingLive() {
            synchronized (segments) { return thinkStart != 0 && thinkEnd == 0; }
        }

        private static boolean showThinking() {
            return GlobalSetting.getShowThinking().getValue();
        }

        private String thoughtLabel() {
            if (thinkStart == 0) return null;
            long end = thinkEnd == 0 ? System.currentTimeMillis() : thinkEnd;
            double seconds = (end - thinkStart) / 1000.0;
            if (seconds < 0) seconds = 0;
            if (seconds > 9999) seconds = 9999;
            return String.format(Locale.ROOT, "%.1f", seconds);
        }

        private void appendThought(String text) {
            synchronized (segments) {
                if (thinkStart == 0) thinkStart = System.currentTimeMillis();
                if (!segments.isEmpty() && segments.getLast().kind.equals("thought")) segments.getLast().text += text;
                else segments.add(new Seg("thought", text));
            }
        }

        private void applyTools(String snapshot) {
            List<String> blocks = toolBlocks(snapshot);
            synchronized (segments) {
                int index = 0;
                for (Seg seg : segments) {
                    if (!seg.kind.equals("tool")) continue;
                    if (index < blocks.size()) seg.text = blocks.get(index);
                    index++;
                }
                while (index < blocks.size()) segments.add(new Seg("tool", blocks.get(index++)));
            }
        }

        private static List<String> toolBlocks(String text) {
            List<String> blocks = new ArrayList<>();
            StringBuilder current = null;
            for (String line : text.split("\n", -1)) {
                boolean header = !line.isEmpty() && (line.charAt(0) == '*' || line.charAt(0) == '+' || line.charAt(0) == '!');
                if (header) {
                    if (current != null) blocks.add(current.toString());
                    current = new StringBuilder(line);
                } else if (current != null) {
                    current.append('\n').append(line);
                }
            }
            if (current != null) blocks.add(current.toString());
            return blocks;
        }

        private void endThought() {
            synchronized (segments) {
                if (thinkStart != 0 && thinkEnd == 0) thinkEnd = System.currentTimeMillis();
            }
        }

        private void drain(boolean flush) {
            while (true) {
                String value = pending.toString();
                String open = earliest(value, "<think>", "<thinking>");
                String close = earliest(value, "</think>", "</thinking>");
                if (!insideThink) {
                    int at = open == null ? -1 : indexOfToken(value, open);
                    if (at >= 0) {
                        appendAnswer(value.substring(0, at));
                        insideThink = true;
                        pending.delete(0, at + open.length());
                        continue;
                    }
                    int hold = flush ? 0 : tail(value, "<think>", "<thinking>");
                    if (hold == value.length()) return;
                    appendAnswer(value.substring(0, value.length() - hold));
                    pending.delete(0, value.length() - hold);
                    return;
                }
                int at = close == null ? -1 : indexOfToken(value, close);
                if (at >= 0) {
                    appendThinking(value.substring(0, at));
                    insideThink = false;
                    pending.delete(0, at + close.length());
                    beginAnswer();
                    continue;
                }
                int hold = flush ? 0 : tail(value, "</think>", "</thinking>");
                if (hold == value.length()) return;
                appendThinking(value.substring(0, value.length() - hold));
                pending.delete(0, value.length() - hold);
                return;
            }
        }

        private void beginAnswer() {
            synchronized (segments) {
                if (answering) return;
                answering = true;
                if (thinkStart != 0 && thinkEnd == 0) thinkEnd = System.currentTimeMillis();
            }
            lastUpdate = 0;
        }

        private void appendThinking(String text) {
            if (answering || text.isEmpty()) return;
            waiting = false;
            appendThought(text);
        }

        private void appendAnswer(String text) {
            if (text.isEmpty()) return;
            beginAnswer();
            synchronized (content) {
                content.append(text);
            }
        }

        private void schedule(boolean force) {
            long now = System.currentTimeMillis();
            if (!force && now - lastUpdate < 50L) return;
            lastUpdate = now;
            if (!updateQueued.compareAndSet(false, true)) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) { updateQueued.set(false); return; }
            client.execute(() -> {
                try {
                    if (stream == streamId && !closed) chatLine.update(view());
                } finally {
                    updateQueued.set(false);
                }
            });
        }

        private static String clean(String text) {
            return text.replace("\r\n", "\n").replace('\r', '\n');
        }

        private static String earliest(String value, String first, String second) {
            int a = indexOfToken(value, first);
            int b = indexOfToken(value, second);
            if (a < 0 && b < 0) return null;
            if (a < 0) return second;
            if (b < 0 || a <= b) return first;
            return second;
        }

        private static int indexOfToken(String value, String token) {
            return value.toLowerCase(Locale.ROOT).indexOf(token);
        }

        private static int tail(String value, String... tokens) {
            int hold = 0;
            for (String token : tokens) {
                int max = Math.min(value.length(), token.length() - 1);
                for (int length = max; length > hold; length--) {
                    if (token.startsWith(value.substring(value.length() - length).toLowerCase(Locale.ROOT))) {
                        hold = length;
                        break;
                    }
                }
            }
            return hold;
        }
    }

    private static final class AiChatLine {
        private int stream;
        private ChatHud hud;
        private ChatHudLine line;

        private void update(View view) {
            if (stream != streamId || view.pieces().isEmpty()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.inGameHud == null || !GlobalSetting.getClientOutput().getValue()) return;
            ChatHud current = client.inGameHud.getChatHud();
            ChatHudAccessor accessor = (ChatHudAccessor) current;
            Text message = message(view);
            if (hud != current) {
                hud = current;
                line = null;
            }
            List<ChatHudLine> messages = accessor.ardion$getMessages();
            if (line != null) {
                int index = identity(messages, line);
                if (index >= 0) {
                    ChatHudLine updated = new ChatHudLine(client.inGameHud.getTicks(), message, line.signature(), line.indicator());
                    replaceLine(accessor, line, updated);
                    line = updated;
                    PINNED.set(line);
                    return;
                }
                PINNED.compareAndSet(line, null);
                return;
            }
            OWN_MESSAGE.set(true);
            try {
                current.addMessage(message);
            } finally {
                OWN_MESSAGE.set(false);
            }
            line = messages.getFirst();
            PINNED.set(line);
        }

        private void fail() {
            if (stream != streamId) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (line == null || hud == null || client.inGameHud == null || hud != client.inGameHud.getChatHud()) {
                show("No text in the AI response", Formatting.RED);
                return;
            }
            ChatHudAccessor accessor = (ChatHudAccessor) hud;
            List<ChatHudLine> messages = accessor.ardion$getMessages();
            int index = identity(messages, line);
            if (index < 0) {
                show("No text in the AI response", Formatting.RED);
                return;
            }
            Text message = Text.literal("[AI] ").formatted(Formatting.LIGHT_PURPLE)
                    .append(Text.literal("No text in the AI response").formatted(Formatting.RED));
            ChatHudLine updated = new ChatHudLine(line.creationTick(), message, line.signature(), line.indicator());
            replaceLine(accessor, line, updated);
            line = updated;
        }

        private static Text message(View view) {
            MutableText message = Text.literal("[AI] ").formatted(Formatting.LIGHT_PURPLE);
            boolean any = false;
            for (Piece piece : view.pieces()) {
                if (any) message.append(Text.literal("\n"));
                any = true;
                switch (piece.kind()) {
                    case "label" -> message.append(Text.literal("Thought for " + piece.text() + "s").formatted(Formatting.GRAY));
                    case "tool" -> appendTools(message, piece.text());
                    case "answer" -> message.append(AiChatText.format(piece.text(), Formatting.WHITE));
                    default -> message.append(AiChatText.format(piece.text(), Formatting.GRAY));
                }
            }
            return message;
        }

        private static void appendTools(MutableText message, String tools) {
            boolean first = true;
            for (String line : tools.split("\n", -1)) {
                if (!first) message.append(Text.literal("\n"));
                first = false;
                if (!line.isEmpty() && (line.charAt(0) == '*' || line.charAt(0) == '+' || line.charAt(0) == '!')) {
                    Formatting color = line.charAt(0) == '!' ? Formatting.RED : line.charAt(0) == '*' ? Formatting.YELLOW : Formatting.GREEN;
                    message.append(Text.literal("● ").formatted(color));
                    message.append(AiChatText.toolLine(line.substring(1).stripLeading()));
                } else {
                    message.append(Text.literal(line).formatted(Formatting.DARK_GRAY));
                }
            }
        }
    }
}
