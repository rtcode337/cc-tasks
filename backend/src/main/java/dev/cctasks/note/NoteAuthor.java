package dev.cctasks.note;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * ノートの書き手。人間(PWA)か Claude Code(MCP)か。
 */
public enum NoteAuthor {

    HUMAN("human"),
    CLAUDE_CODE("claude_code");

    private final String wireValue;

    NoteAuthor(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static NoteAuthor from(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(a -> a.wireValue.equalsIgnoreCase(value) || a.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("author が不正です: " + value));
    }
}
