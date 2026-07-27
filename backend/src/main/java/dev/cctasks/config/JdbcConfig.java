package dev.cctasks.config;

import java.sql.JDBCType;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

import dev.cctasks.task.TaskStatus;

import org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

/**
 * SQLite は日時型も真偽値も持たないため、Java 側の型との橋渡しを明示する。
 *
 * <p>{@link JdbcRepositoriesAutoConfiguration} が用意する構成を継承して
 * カスタムコンバータだけを差し込む。
 */
@Configuration(proxyBeanMethods = false)
public class JdbcConfig extends AbstractJdbcConfiguration {

    /** DDL 上は TEXT。ISO 8601 (UTC, ミリ秒精度) で保存する。 */
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(java.time.ZoneOffset.UTC);

    @Override
    protected List<?> userConverters() {
        return List.of(
                InstantToJdbcValueConverter.INSTANCE,
                StringToInstantConverter.INSTANCE,
                TaskStatusToStringConverter.INSTANCE,
                StringToTaskStatusConverter.INSTANCE,
                BooleanToIntegerConverter.INSTANCE,
                IntegerToBooleanConverter.INSTANCE);
    }

    /** SQLite に BOOLEAN 型は無く、DDL 上は INTEGER 0/1。 */
    @WritingConverter
    enum BooleanToIntegerConverter implements Converter<Boolean, Integer> {
        INSTANCE;

        @Override
        public Integer convert(Boolean source) {
            return source ? 1 : 0;
        }
    }

    @ReadingConverter
    enum IntegerToBooleanConverter implements Converter<Integer, Boolean> {
        INSTANCE;

        @Override
        public Boolean convert(Integer source) {
            return source != 0;
        }
    }

    /**
     * Instant を TEXT 列に ISO 8601 で書く。
     *
     * <p>単純な {@code Converter<Instant, String>} では効かない。Spring Data JDBC は
     * {@code Temporal} 系プロパティの列型を {@code java.sql.Timestamp} と決めてから
     * 変換先を探すため、標準の Instant→Timestamp コンバータが先に一致してしまい
     * (SQLite にはエポックミリ秒が入る)。{@link JdbcValue} を返す形にすると
     * 「列型の推測」を飛ばして、こちらの指定がそのまま使われる。
     */
    @WritingConverter
    enum InstantToJdbcValueConverter implements Converter<Instant, JdbcValue> {
        INSTANCE;

        @Override
        public JdbcValue convert(Instant source) {
            return JdbcValue.of(TIMESTAMP_FORMAT.format(source), JDBCType.VARCHAR);
        }
    }

    @ReadingConverter
    enum StringToInstantConverter implements Converter<String, Instant> {
        INSTANCE;

        @Override
        public Instant convert(String source) {
            return Instant.parse(source);
        }
    }

    @WritingConverter
    enum TaskStatusToStringConverter implements Converter<TaskStatus, String> {
        INSTANCE;

        @Override
        public String convert(TaskStatus source) {
            return source.wireValue();
        }
    }

    @ReadingConverter
    enum StringToTaskStatusConverter implements Converter<String, TaskStatus> {
        INSTANCE;

        @Override
        public TaskStatus convert(String source) {
            return TaskStatus.from(source);
        }
    }

}
