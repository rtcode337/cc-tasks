package dev.cctasks.config;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import dev.cctasks.task.Task;
import dev.cctasks.task.TaskRepository;
import dev.cctasks.task.TaskStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 起動直後に書き込み経路を一度だけ素振りしておく。
 *
 * コンテナ再起動後の最初の書き込みは、
 * (1) トランザクション・Spring Data JDBC の INSERT 組み立て・型コンバータの
 *     クラスロード & JIT が初回リクエストに乗る
 * (2) 読み取りと違いページキャッシュで返せず、ディスク(NAS の HDD なら
 *     スピンアップ待ちも)を直撃する
 * の二重取りで極端に遅くなる。ここで INSERT + DELETE を 1 トランザクション
 * コミットしておき、そのコストをデプロイ時に前払いする。
 */
@Component
public class WriteWarmup {

    private static final Logger log = LoggerFactory.getLogger(WriteWarmup.class);

    private final TaskRepository taskRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public WriteWarmup(TaskRepository taskRepository, TransactionTemplate transactionTemplate,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        long started = System.nanoTime();
        try {
            // INSERT + DELETE を同一トランザクションで commit する。行は残らないが
            // WAL への書き込みは実際に発生するので、ディスク側の初回コストも払える
            transactionTemplate.executeWithoutResult(status -> {
                Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
                Task saved = taskRepository.save(new Task(null, null, "(warmup)",
                        null, null, null, TaskStatus.TODO, now, now));
                taskRepository.deleteById(saved.id());
            });
            log.info("書き込み経路をウォームアップしました ({} ms)",
                    (System.nanoTime() - started) / 1_000_000);
        } catch (Exception e) {
            // ウォームアップは失敗しても実害がない(初回書き込みが遅いだけ)ので起動は続ける
            log.warn("書き込みウォームアップに失敗しました(起動は続行)", e);
        }
    }
}
