package dev.cctasks.task;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface TaskRepository extends ListCrudRepository<Task, Long> {

    /**
     * projectId / status いずれも任意の絞り込み。null は「絞り込まない」を意味する。
     */
    // 並びは作成日時降順で固定する。更新で順番が入れ替わると探しづらいため
    // (updated_at ではなく created_at を使う)。
    @Query("""
            SELECT * FROM tasks
            WHERE (:projectId IS NULL OR project_id = :projectId)
              AND (:status IS NULL OR status = :status)
            ORDER BY created_at DESC, id DESC
            """)
    List<Task> search(Long projectId, String status);

    /**
     * status 未指定の MCP list_tasks 用: done 以外を返す。
     */
    @Query("""
            SELECT * FROM tasks
            WHERE project_id = :projectId AND status <> 'done'
            ORDER BY created_at DESC, id DESC
            """)
    List<Task> findOpenByProjectId(Long projectId);

    /** 未完了(done 以外)。トップと一覧の既定表示。 */
    @Query("""
            SELECT * FROM tasks
            WHERE (:projectId IS NULL OR project_id = :projectId)
              AND status <> 'done'
            ORDER BY created_at DESC, id DESC
            """)
    List<Task> searchActive(Long projectId);

    /** 完了(done)をページングで返す。件数が増えるため LIMIT/OFFSET する。 */
    @Query("""
            SELECT * FROM tasks
            WHERE (:projectId IS NULL OR project_id = :projectId)
              AND status = 'done'
            ORDER BY created_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<Task> searchDone(Long projectId, int limit, int offset);

    @Query("""
            SELECT count(*) FROM tasks
            WHERE (:projectId IS NULL OR project_id = :projectId)
              AND status = 'done'
            """)
    long countDone(Long projectId);

    @Query("SELECT count(*) FROM tasks WHERE project_id = :projectId")
    long countByProjectId(Long projectId);
}
