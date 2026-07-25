package dev.cctasks.project;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.ListCrudRepository;

public interface ProjectRepository extends ListCrudRepository<Project, Long>, CrudRepository<Project, Long> {

    Optional<Project> findByName(String name);

    @Query("SELECT * FROM projects WHERE archived = :archived ORDER BY name")
    List<Project> findByArchivedOrderByName(boolean archived);

    @Query("SELECT * FROM projects ORDER BY name")
    List<Project> findAllOrderByName();
}
