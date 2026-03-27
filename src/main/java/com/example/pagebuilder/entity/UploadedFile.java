package com.example.pagebuilder.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "uploaded_file")
public class UploadedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    // EXCEL, PPT, IMAGE
    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    // PPT/Excel 텍스트 추출 결과
    @Lob
    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    // 이미지 파일 base64 데이터 (IMAGE 타입일 때 사용)
    @Lob
    @Column(name = "image_data", columnDefinition = "TEXT")
    private String imageData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId() { return id; }

    public Member getMember() { return member; }
    public void setMember(Member member) { this.member = member; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }

    public String getImageData() { return imageData; }
    public void setImageData(String imageData) { this.imageData = imageData; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
