package dev.cctasks.task;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * タスクのライフサイクル。todo → in_progress → done。
 * 遷移に制約は設けない(任意の状態から任意の状態へ変更可)。
 */
public enum TaskStatus {

    TODO("todo"),
    IN_PROGRESS("in_progress"),
    DONE("done");

    private final String wireValue;

    TaskStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static TaskStatus from(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(s -> s.wireValue.equalsIgnoreCase(value) || s.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "status は todo / in_progress / done のいずれかを指定してください: " + value));
    }
}
