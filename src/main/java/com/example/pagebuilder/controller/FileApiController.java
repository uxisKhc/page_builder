package com.example.pagebuilder.controller;

import com.example.pagebuilder.dto.FileDto;
import com.example.pagebuilder.entity.Member;
import com.example.pagebuilder.service.FileParseService;
import com.example.pagebuilder.service.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileApiController {

    @Autowired private MemberService memberService;
    @Autowired private FileParseService fileParseService;

    /**
     * 파일 업로드 (PPT/Excel/이미지)
     * POST /api/files/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    Authentication auth) {
        try {
            Member member = memberService.findByUsername(auth.getName());
            FileDto result = fileParseService.uploadAndParse(file, member);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 내 파일 목록 (imageData 제외)
     * GET /api/files
     */
    @GetMapping
    public ResponseEntity<List<FileDto>> list(Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        return ResponseEntity.ok(fileParseService.getMyFiles(member));
    }

    /**
     * 파일 상세 조회 (이미지 base64 포함) — 에디터 미리보기용
     * GET /api/files/{id}/content
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<?> content(@PathVariable Long id, Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        return fileParseService.getFileWithContent(id, member)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 파일 삭제
     * DELETE /api/files/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> delete(@PathVariable Long id, Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        fileParseService.deleteFile(id, member);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
