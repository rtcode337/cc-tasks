package dev.cctasks.config;

import org.springframework.data.jdbc.core.dialect.JdbcArrayColumns;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.relational.core.dialect.AbstractDialect;
import org.springframework.data.relational.core.dialect.IdGeneration;
import org.springframework.data.relational.core.dialect.LimitClause;
import org.springframework.data.relational.core.dialect.LockClause;
import org.springframework.data.relational.core.sql.IdentifierProcessing;
import org.springframework.data.relational.core.sql.IdentifierProcessing.LetterCasing;
import org.springframework.data.relational.core.sql.IdentifierProcessing.Quoting;
import org.springframework.data.relational.core.sql.LockOptions;

/**
 * Spring Data JDBC は SQLite 方言を同梱していないため自前で用意する。
 *
 * <p>ポイント:
 * <ul>
 *   <li>LIMIT / OFFSET は MySQL・PostgreSQL と同じ構文。ただし OFFSET 単独指定は
 *       SQLite の文法上許されないため {@code LIMIT -1 OFFSET n} と書く。</li>
 *   <li>識別子は大文字化してはいけない(SQLite はクォートしない識別子を宣言時のまま扱う)ため
 *       {@link LetterCasing#AS_IS} を使う。</li>
 *   <li>SQLite に行ロックは無いので LOCK 句は空文字を返す。</li>
 *   <li>{@code INTEGER PRIMARY KEY AUTOINCREMENT} の採番は JDBC ドライバの
 *       getGeneratedKeys で取得できるため、キー列名の明示は不要。</li>
 * </ul>
 */
public class SqliteDialect extends AbstractDialect implements JdbcDialect {

    public static final SqliteDialect INSTANCE = new SqliteDialect();

    private static final IdentifierProcessing IDENTIFIER_PROCESSING =
            IdentifierProcessing.create(Quoting.ANSI, LetterCasing.AS_IS);

    private static final IdGeneration ID_GENERATION = new IdGeneration() {
        @Override
        public boolean driverRequiresKeyColumnNames() {
            return false;
        }

        @Override
        public boolean supportedForBatchOperations() {
            return false;
        }

        @Override
        public boolean sequencesSupported() {
            return false;
        }
    };

    private static final LimitClause LIMIT_CLAUSE = new LimitClause() {
        @Override
        public String getLimit(long limit) {
            return "LIMIT " + limit;
        }

        @Override
        public String getOffset(long offset) {
            // SQLite は OFFSET 単独を許さない。無制限を表す -1 を LIMIT に置く。
            return "LIMIT -1 OFFSET " + offset;
        }

        @Override
        public String getLimitOffset(long limit, long offset) {
            return "LIMIT %d OFFSET %d".formatted(limit, offset);
        }

        @Override
        public Position getClausePosition() {
            return Position.AFTER_ORDER_BY;
        }
    };

    private static final LockClause LOCK_CLAUSE = new LockClause() {
        @Override
        public String getLock(LockOptions lockOptions) {
            return "";
        }

        @Override
        public Position getClausePosition() {
            return Position.AFTER_ORDER_BY;
        }
    };

    protected SqliteDialect() {
    }

    @Override
    public LimitClause limit() {
        return LIMIT_CLAUSE;
    }

    @Override
    public LockClause lock() {
        return LOCK_CLAUSE;
    }

    @Override
    public JdbcArrayColumns getArraySupport() {
        return JdbcArrayColumns.Unsupported.INSTANCE;
    }

    @Override
    public IdentifierProcessing getIdentifierProcessing() {
        return IDENTIFIER_PROCESSING;
    }

    @Override
    public IdGeneration getIdGeneration() {
        return ID_GENERATION;
    }
}
