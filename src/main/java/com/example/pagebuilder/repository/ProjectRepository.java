package com.example.pagebuilder.repository;

import com.example.pagebuilder.entity.Member;
import com.example.pagebuilder.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByMemberOrderByCreatedAtDesc(Member member);
    Optional<Project> findByIdAndMember(Long id, Member member);
    Optional<Project> findByUuid(String uuid);
}
