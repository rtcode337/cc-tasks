package dev.cctasks.task;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface TaskRepository extends ListCrudRepository<Task, Long> {

    /**
     * projectId / status いずれも任意の絞り込み。null は「絞り込まない」を意味する。
     */
    // 並びは「プロジェクト内の手動並び順 (sort_order 昇順) → 作成日時降順」。
    // sort_order は *プロジェクト内* の順序なので、projectId で絞らない全体一覧では
    // 使わない(プロジェクトをまたいで番号が混ざると探しづらい)。全体は従来どおり
    // 作成日時降順のまま。updated_at ではなく created_at なのは、更新で順番が
    // 入れ替わると探しづらいため。
    @Query("""
            SELECT * FROM tasks
            WHERE (:projectId IS NULL OR project_id = :projectId)
              AND (:status IS NULL OR status = :status)
            ORDER BY CASE WHEN :projectId IS NULL THEN 0 ELSE sort_order END,
                     created_at DESC, id DESC
            """)
    List<Task> search(Long projectId, String status);

    /** 未完了(done 以外)。トップと一覧の既定表示。 */
    @Query("""
            SELECT * FROM tasks
            WHERE (:projectId IS NULL OR project_id = :projectId)
              AND status <> 'done'
            ORDER BY CASE WHEN :projectId IS NULL THEN 0 ELSE sort_order END,
                     created_at DESC, id DESC
            """)
    List<Task> searchActive(Long projectId);

    /** 完了(done)をページングで返す。件数が増えるため LIMIT/OFFSET する。 */
    // 完了分は「片付いたものの記録」なので手動並び順は見ず、作成日時降順で固定する。
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

    /** 未完了(done 以外)の件数。プロジェクトをアーカイブしてよいかの判定に使う。 */
    @Query("SELECT count(*) FROM tasks WHERE project_id = :projectId AND status <> 'done'")
    long countIncompleteByProjectId(Long projectId);

    /** プロジェクトを消すときに、紐づくタスクも一緒に落とす。 */
    @Modifying
    @Query("DELETE FROM tasks WHERE project_id = :projectId")
    void deleteByProjectId(Long projectId);

    /** 並び替え専用。updated_at は触らない(並び替えはタスクの「動き」ではないため)。 */
    @Modifying
    @Query("UPDATE tasks SET sort_order = :sortOrder WHERE id = :id")
    void updateSortOrder(Long id, int sortOrder);
}
