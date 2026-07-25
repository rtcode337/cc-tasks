package dev.cctasks.config;

import java.util.Locale;
import java.util.Optional;

import org.springframework.data.jdbc.core.dialect.DialectResolver;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * {@code META-INF/spring.factories} 経由で Spring Data JDBC に SQLite 方言を認識させる。
 * 標準の DefaultDialectProvider は SQLite を知らないため、これが無いと起動時に
 * NoDialectException で落ちる。
 */
public class SqliteDialectProvider implements DialectResolver.JdbcDialectProvider {

    @Override
    public Optional<Dialect> getDialect(JdbcOperations operations) {
        try {
            String productName = operations.execute((ConnectionCallback<String>) connection ->
                    connection.getMetaData().getDatabaseProductName());
            if (productName != null && productName.toLowerCase(Locale.ROOT).contains("sqlite")) {
                return Optional.of(SqliteDialect.INSTANCE);
            }
        }
        catch (RuntimeException ex) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
