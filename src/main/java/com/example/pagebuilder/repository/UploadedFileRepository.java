package com.example.pagebuilder.repository;

import com.example.pagebuilder.entity.Member;
import com.example.pagebuilder.entity.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {
    List<UploadedFile> findByMemberOrderByCreatedAtDesc(Member member);
    Optional<UploadedFile> findByIdAndMember(Long id, Member member);
}
