package dev.cctasks.project;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.ListCrudRepository;

public interface ProjectRepository extends ListCrudRepository<Project, Long>, CrudRepository<Project, Long> {

    Optional<Project> findByName(String name);

    @Query("SELECT * FROM projects WHERE archived = :archived ORDER BY sort_order, name")
    List<Project> findByArchivedOrdered(boolean archived);

    @Query("SELECT * FROM projects ORDER BY sort_order, name")
    List<Project> findAllOrdered();

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM projects")
    int maxSortOrder();

    @Modifying
    @Query("UPDATE projects SET sort_order = :sortOrder WHERE id = :id")
    void updateSortOrder(Long id, int sortOrder);
}
