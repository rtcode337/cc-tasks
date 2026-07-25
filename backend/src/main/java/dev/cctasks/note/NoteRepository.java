package dev.cctasks.note;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface NoteRepository extends ListCrudRepository<Note, Long> {

    /** 新しい順(タイムライン表示・プロンプト生成の既定順)。 */
    @Query("SELECT * FROM notes WHERE task_id = :taskId ORDER BY created_at DESC, id DESC")
    List<Note> findByTaskIdNewestFirst(Long taskId);

    @Modifying
    @Query("DELETE FROM notes WHERE task_id = :taskId")
    int deleteByTaskId(Long taskId);
}
