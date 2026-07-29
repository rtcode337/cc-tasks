package dev.cctasks.task;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * タスクの状態。未着手(todo)・着手中(in_progress)・完了(done)の 3 つ。
 * 遷移に制約は設けない(任意の状態から任意の状態へ変更可)。
 *
 * <p>着手中は 2026-07 に一度廃止したが、「依頼はしたが動作確認が済んでおらず
 * 閉じられない」を表すために復活した。廃止中に作られた DB は CHECK 制約が
 * in_progress を許さないため、{@code SchemaMigrations} がテーブルを作り直す。
 */
public enum TaskStatus {

    /** 未着手。 */
    TODO("todo"),
    /** 着手中。依頼済みだが動作確認が済んでいない。 */
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
