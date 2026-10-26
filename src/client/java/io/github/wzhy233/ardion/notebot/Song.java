package io.github.wzhy233.ardion.notebot;

import java.util.*;

public record Song(String filename, String name, String author, String format, Map<Integer, List<Note>> notes, Set<Note> requirements, int length) {
    public Song(String filename, String name, String author, String format, Map<Integer, List<Note>> notes) {
        this(
                filename,
                name,
                author,
                format,
                Collections.unmodifiableMap(notes),
                collectRequirements(notes.values()),
                notes.keySet().stream().max(Comparator.naturalOrder()).orElse(0)
        );
    }

    @Override
    public String name() {
        if (this.name == null || this.name.isBlank()) {
            return stripExtension(this.filename);
        }
        return this.name;
    }

    private static Set<Note> collectRequirements(Collection<List<Note>> values) {
        Set<Note> requirements = new HashSet<>();
        for (List<Note> notes : values) {
            requirements.addAll(notes);
        }
        return Collections.unmodifiableSet(requirements);
    }

    private static String stripExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index >= 0 ? filename.substring(0, index) : filename;
    }
}
