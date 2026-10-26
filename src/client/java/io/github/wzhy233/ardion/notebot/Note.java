package io.github.wzhy233.ardion.notebot;

import java.util.Objects;

public record Note(int pitch, int instrument) {
    public Note {
        if (pitch < 0) {
            pitch = 0;
        } else if (pitch > 24) {
            pitch = 24;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.pitch, this.instrument);
    }
}
