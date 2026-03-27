package com.example.pagebuilder.dto;

import com.example.pagebuilder.entity.UploadedFile;

import java.time.LocalDateTime;

public class FileDto {

    private Long id;
    private String originalFilename;
    private String fileType;
    private Long fileSize;
    private String extractedTextPreview; // 목록 표시용 200자 미리보기
    private boolean hasImage;            // IMAGE 타입 여부
    private String imageData;            // base64 (에디터 미리보기용, 목록에선 제외)
    private LocalDateTime createdAt;

    // 목록용 (imageData 제외)
    public static FileDto from(UploadedFile file) {
        FileDto dto = new FileDto();
        dto.id = file.getId();
        dto.originalFilename = file.getOriginalFilename();
        dto.fileType = file.getFileType();
        dto.fileSize = file.getFileSize();
        dto.hasImage = "IMAGE".equals(file.getFileType());
        dto.createdAt = file.getCreatedAt();

        if ("IMAGE".equals(file.getFileType())) {
            dto.extractedTextPreview = "이미지 파일";
        } else if (file.getExtractedText() != null) {
            String text = file.getExtractedText();
            dto.extractedTextPreview = text.length() > 200 ? text.substring(0, 200) + "..." : text;
        }
        return dto;
    }

    // 에디터용 (imageData 포함)
    public static FileDto withImage(UploadedFile file) {
        FileDto dto = from(file);
        dto.imageData = file.getImageData();
        return dto;
    }

    public Long getId() { return id; }
    public String getOriginalFilename() { return originalFilename; }
    public String getFileType() { return fileType; }
    public Long getFileSize() { return fileSize; }
    public String getExtractedTextPreview() { return extractedTextPreview; }
    public boolean isHasImage() { return hasImage; }
    public String getImageData() { return imageData; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
