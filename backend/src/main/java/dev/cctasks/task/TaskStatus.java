package dev.cctasks.task;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * タスクの状態。未完了(todo)と完了(done)の 2 つだけ。
 * 遷移に制約は設けない(完了から未完了へ戻せる)。
 *
 * <p>2026-07 に「着手中(in_progress)」を廃止した。wire 値の {@code todo} は
 * 「未着手」ではなく「未完了」の意味になっている(既存 DB との互換のため名前は据え置き)。
 */
public enum TaskStatus {

    /** 未完了。 */
    TODO("todo"),
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
                        "status は todo / done のいずれかを指定してください: " + value));
    }
}
