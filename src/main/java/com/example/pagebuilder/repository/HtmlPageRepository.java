package com.example.pagebuilder.repository;

import com.example.pagebuilder.entity.HtmlPage;
import com.example.pagebuilder.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HtmlPageRepository extends JpaRepository<HtmlPage, Long> {
    List<HtmlPage> findByMemberOrderByCreatedAtDesc(Member member);
    Optional<HtmlPage> findByUuid(String uuid);
    Optional<HtmlPage> findByIdAndMember(Long id, Member member);
}
