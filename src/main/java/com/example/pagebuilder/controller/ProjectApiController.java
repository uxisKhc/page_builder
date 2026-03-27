package com.example.pagebuilder.controller;

import com.example.pagebuilder.dto.ProjectDto;
import com.example.pagebuilder.entity.Member;
import com.example.pagebuilder.service.MemberService;
import com.example.pagebuilder.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectApiController {

    @Autowired private MemberService memberService;
    @Autowired private ProjectService projectService;

    /** GET /api/projects — 내 프로젝트 목록 */
    @GetMapping
    public ResponseEntity<List<ProjectDto>> list(Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        return ResponseEntity.ok(projectService.getMyProjects(member));
    }

    /** POST /api/projects — 프로젝트 생성 */
    @PostMapping
    public ResponseEntity<ProjectDto> create(@RequestBody Map<String, String> body, Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        ProjectDto dto = projectService.createProject(body.get("name"), body.get("description"), member);
        return ResponseEntity.ok(dto);
    }

    /** GET /api/projects/{id} — 프로젝트 상세 */
    @GetMapping("/{id}")
    public ResponseEntity<ProjectDto> get(@PathVariable Long id, Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        return ResponseEntity.ok(projectService.getMyProject(id, member));
    }

    /** PUT /api/projects/{id} — 프로젝트 수정 */
    @PutMapping("/{id}")
    public ResponseEntity<ProjectDto> update(@PathVariable Long id,
                                              @RequestBody Map<String, String> body,
                                              Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        return ResponseEntity.ok(projectService.updateProject(id, body.get("name"), body.get("description"), member));
    }

    /** DELETE /api/projects/{id} — 프로젝트 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> delete(@PathVariable Long id, Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        projectService.deleteProject(id, member);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** POST /api/projects/{id}/pages — 페이지 추가 */
    @PostMapping("/{id}/pages")
    public ResponseEntity<ProjectDto> addPage(@PathVariable Long id,
                                               @RequestBody Map<String, String> body,
                                               Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        return ResponseEntity.ok(projectService.addPage(id, body.get("pageName"), member));
    }

    /** DELETE /api/projects/{id}/pages/{pageId} — 페이지 제거 */
    @DeleteMapping("/{id}/pages/{pageId}")
    public ResponseEntity<ProjectDto> removePage(@PathVariable Long id,
                                                  @PathVariable Long pageId,
                                                  Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        return ResponseEntity.ok(projectService.removePage(id, pageId, member));
    }

    /** PUT /api/projects/pages/{pageId}/name — 페이지 이름 변경 */
    @PutMapping("/pages/{pageId}/name")
    public ResponseEntity<Map<String, Boolean>> renamePage(@PathVariable Long pageId,
                                                            @RequestBody Map<String, String> body,
                                                            Authentication auth) {
        Member member = memberService.findByUsername(auth.getName());
        projectService.renamePage(pageId, body.get("pageName"), member);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
