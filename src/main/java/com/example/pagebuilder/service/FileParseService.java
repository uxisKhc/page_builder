package com.example.pagebuilder.service;

import com.example.pagebuilder.dto.FileDto;
import com.example.pagebuilder.entity.Member;
import com.example.pagebuilder.entity.UploadedFile;
import com.example.pagebuilder.repository.UploadedFileRepository;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class FileParseService {

    private static final Logger log = LoggerFactory.getLogger(FileParseService.class);

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    /**
     * 파일 업로드 및 파싱
     * 지원: .xlsx/.xls (Excel), .pptx/.ppt (PPT), .png/.jpg/.jpeg/.gif/.webp (이미지)
     */
    public FileDto uploadAndParse(MultipartFile file, Member member) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) throw new IllegalArgumentException("파일명이 없습니다.");

        String ext = filename.toLowerCase();
        String fileType;
        String extractedText = null;
        String imageData = null;

        if (ext.endsWith(".xlsx") || ext.endsWith(".xls")) {
            fileType = "EXCEL";
            extractedText = parseExcel(file);

        } else if (ext.endsWith(".pptx")) {
            fileType = "PPT";
            extractedText = parsePptx(file);

        } else if (ext.endsWith(".ppt")) {
            fileType = "PPT";
            extractedText = parsePpt(file);

        } else if (ext.endsWith(".png") || ext.endsWith(".jpg") || ext.endsWith(".jpeg")
                || ext.endsWith(".gif") || ext.endsWith(".webp")) {
            fileType = "IMAGE";
            // 이미지를 base64로 인코딩하여 저장
            byte[] bytes = file.getBytes();
            imageData = Base64.getEncoder().encodeToString(bytes);
            String mimeType = detectMimeType(ext);
            extractedText = "data:" + mimeType + ";base64," + imageData; // 전체 data URL을 text에도 저장

        } else {
            throw new IllegalArgumentException(
                "지원하지 않는 파일 형식입니다.\n" +
                "- 이미지: PNG, JPG, GIF, WEBP\n" +
                "- 문서: PPTX, PPT, XLSX, XLS"
            );
        }

        UploadedFile entity = new UploadedFile();
        entity.setMember(member);
        entity.setOriginalFilename(filename);
        entity.setFileType(fileType);
        entity.setFileSize(file.getSize());
        entity.setExtractedText(extractedText);
        entity.setImageData(imageData);

        return FileDto.from(uploadedFileRepository.save(entity));
    }

    /**
     * 내 파일 목록
     */
    @Transactional(readOnly = true)
    public List<FileDto> getMyFiles(Member member) {
        return uploadedFileRepository.findByMemberOrderByCreatedAtDesc(member)
                .stream()
                .map(FileDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 파일 내용 조회 (이미지 포함) — 에디터에서 미리보기용
     */
    @Transactional(readOnly = true)
    public Optional<FileDto> getFileWithContent(Long fileId, Member member) {
        return uploadedFileRepository.findByIdAndMember(fileId, member)
                .map(FileDto::withImage);
    }

    /**
     * 파일 삭제
     */
    public void deleteFile(Long fileId, Member member) {
        UploadedFile file = uploadedFileRepository.findByIdAndMember(fileId, member)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다."));
        uploadedFileRepository.delete(file);
    }

    /**
     * AI 참고용 데이터 수집
     * - TEXT 계열: extractedText 반환
     * - IMAGE: imageData(base64) 반환
     */
    @Transactional(readOnly = true)
    public ReferenceData collectReference(List<Long> fileIds, Member member) {
        if (fileIds == null || fileIds.isEmpty()) return ReferenceData.empty();

        StringBuilder textSb = new StringBuilder();
        java.util.List<String> images = new java.util.ArrayList<>();

        for (Long fileId : fileIds) {
            uploadedFileRepository.findByIdAndMember(fileId, member).ifPresent(f -> {
                if ("IMAGE".equals(f.getFileType()) && f.getImageData() != null) {
                    images.add(f.getImageData()); // base64
                } else if (f.getExtractedText() != null) {
                    textSb.append("=== ").append(f.getOriginalFilename()).append(" ===\n");
                    String text = f.getExtractedText();
                    // 3000자 제한
                    textSb.append(text, 0, Math.min(text.length(), 3000));
                    textSb.append("\n\n");
                }
            });
        }

        return new ReferenceData(
            textSb.length() > 0 ? textSb.toString() : null,
            images.isEmpty() ? null : images
        );
    }

    // ─────────────────────────────────────────
    // 파싱 내부 메서드
    // ─────────────────────────────────────────

    private String parseExcel(MultipartFile file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            org.apache.poi.ss.usermodel.DataFormatter formatter =
                    new org.apache.poi.ss.usermodel.DataFormatter();

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                sb.append("[시트: ").append(sheet.getSheetName()).append("]\n");

                for (Row row : sheet) {
                    StringBuilder rowText = new StringBuilder();
                    for (Cell cell : row) {
                        String val = formatter.formatCellValue(cell).trim();
                        if (!val.isEmpty()) rowText.append(val).append("\t");
                    }
                    String rowStr = rowText.toString().trim();
                    if (!rowStr.isEmpty()) sb.append(rowStr).append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String parsePptx(MultipartFile file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = file.getInputStream();
             XMLSlideShow ppt = new XMLSlideShow(is)) {

            List<XSLFSlide> slides = ppt.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                sb.append("[슬라이드 ").append(i + 1).append("]\n");
                XSLFSlide slide = slides.get(i);

                // 슬라이드 제목 우선 출력
                String title = slide.getSlideName();
                if (title != null && !title.isBlank()) {
                    sb.append("제목: ").append(title.trim()).append("\n");
                }

                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        String text = ((XSLFTextShape) shape).getText();
                        if (text != null && !text.isBlank()) {
                            sb.append(text.trim()).append("\n");
                        }
                    }
                }
                sb.append("\n");
            }
        }
        log.debug("PPTX 파싱 완료: {}자", sb.length());
        return sb.toString();
    }

    private String parsePpt(MultipartFile file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = file.getInputStream();
             HSLFSlideShow ppt = new HSLFSlideShow(is)) {

            List<HSLFSlide> slides = ppt.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                sb.append("[슬라이드 ").append(i + 1).append("]\n");
                for (HSLFShape shape : slides.get(i).getShapes()) {
                    if (shape instanceof HSLFTextShape) {
                        String text = ((HSLFTextShape) shape).getText();
                        if (text != null && !text.isBlank()) {
                            sb.append(text.trim()).append("\n");
                        }
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String detectMimeType(String ext) {
        switch (ext) {
            case ".jpg": case ".jpeg": return "image/jpeg";
            case ".gif":  return "image/gif";
            case ".webp": return "image/webp";
            default:      return "image/png";
        }
    }

    // ─────────────────────────────────────────
    // 참고 데이터 묶음
    // ─────────────────────────────────────────

    public static class ReferenceData {
        public final String text;
        public final List<String> images; // base64 목록

        public ReferenceData(String text, List<String> images) {
            this.text = text;
            this.images = images;
        }

        public boolean isEmpty() { return text == null && (images == null || images.isEmpty()); }
        public boolean hasImages() { return images != null && !images.isEmpty(); }

        public static ReferenceData empty() { return new ReferenceData(null, null); }
    }
}
