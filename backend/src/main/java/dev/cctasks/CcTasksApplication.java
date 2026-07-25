package dev.cctasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import dev.cctasks.config.CcTasksProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CcTasksProperties.class)
public class CcTasksApplication {

    /** application.yml の既定値と揃える。 */
    private static final String DEFAULT_DB_PATH = "./data/cctasks.db";
    private static final String DEFAULT_SESSION_DIR = "./data/sessions";

    public static void main(String[] args) {
        ensureDbDirectoryExists();
        ensureSessionDirectoryExists();
        SpringApplication.run(CcTasksApplication.class, args);
    }

    /**
     * SQLite は親ディレクトリを自動生成しない。Docker のボリュームマウント先は
     * 存在する前提だが、ローカル実行や初回起動で転ばないようにここで作っておく。
     */
    private static void ensureDbDirectoryExists() {
        String dbPath = System.getenv().getOrDefault("DB_PATH", DEFAULT_DB_PATH);
        Path parent = Paths.get(dbPath).toAbsolutePath().getParent();
        createDirectories(parent, "SQLite の保存先");
    }

    /** セッション永続化(FileStore)の保存先。無いと Tomcat が書けずに失敗する。 */
    private static void ensureSessionDirectoryExists() {
        String sessionDir = System.getenv().getOrDefault("SESSION_DIR", DEFAULT_SESSION_DIR);
        createDirectories(Paths.get(sessionDir).toAbsolutePath(), "セッションの保存先");
    }

    private static void createDirectories(Path dir, String what) {
        if (dir == null) {
            return;
        }
        try {
            Files.createDirectories(dir);
        }
        catch (IOException ex) {
            throw new IllegalStateException(what + "ディレクトリを作成できません: " + dir, ex);
        }
    }
}
