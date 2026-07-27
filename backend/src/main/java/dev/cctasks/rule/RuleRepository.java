package dev.cctasks.rule;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface RuleRepository extends ListCrudRepository<Rule, Long> {

    /** 表示順。連結もこの順で行うため、並び順がそのままルール集の並びになる。 */
    @Query("SELECT * FROM rules ORDER BY sort_order, id")
    List<Rule> findAllOrdered();

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM rules")
    int maxSortOrder();

    /** 並び替え専用。updated_at は触らない(並び替えは内容の変更ではないため)。 */
    @Modifying
    @Query("UPDATE rules SET sort_order = :sortOrder WHERE id = :id")
    void updateSortOrder(Long id, int sortOrder);
}
