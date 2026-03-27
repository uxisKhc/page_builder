package com.example.pagebuilder.controller;

import com.example.pagebuilder.dto.FileDto;
import com.example.pagebuilder.dto.PageDto;
import com.example.pagebuilder.dto.ProjectDto;
import com.example.pagebuilder.entity.HtmlPage;
import com.example.pagebuilder.entity.Member;
import com.example.pagebuilder.service.FileParseService;
import com.example.pagebuilder.service.MemberService;
import com.example.pagebuilder.service.PageService;
import com.example.pagebuilder.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@Controller
public class ViewController {

    @Autowired private MemberService memberService;
    @Autowired private PageService pageService;
    @Autowired private FileParseService fileParseService;
    @Autowired private ProjectService projectService;

    /**
     * 대시보드 — 내 페이지 목록 + 파일 목록
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, Model model) {
        Member member = memberService.findByUsername(auth.getName());
        List<PageDto> pages = pageService.getMyPages(member);
        List<FileDto> files = fileParseService.getMyFiles(member);

        model.addAttribute("pages", pages);
        model.addAttribute("files", files);
        model.addAttribute("username", member.getUsername());
        return "dashboard";
    }

    /**
     * 에디터 — 신규 페이지 작성
     */
    @GetMapping("/editor")
    public String editorNew(Authentication auth, Model model) {
        Member member = memberService.findByUsername(auth.getName());
        List<FileDto> files = fileParseService.getMyFiles(member);

        model.addAttribute("files", files);
        model.addAttribute("username", member.getUsername());
        model.addAttribute("pageId", (Object) null);
        return "editor";
    }

    /**
     * 에디터 — 기존 페이지 수정
     */
    @GetMapping("/editor/{pageId}")
    public String editorEdit(@PathVariable Long pageId, Authentication auth, Model model) {
        Member member = memberService.findByUsername(auth.getName());
        PageDto page = pageService.getMyPage(pageId, member);
        List<FileDto> files = fileParseService.getMyFiles(member);
        model.addAttribute("page", page);
        model.addAttribute("files", files);
        model.addAttribute("username", member.getUsername());
        model.addAttribute("pageId", pageId);
        return "editor";
    }

    /**
     * 프로젝트 에디터 — 멀티 페이지 편집
     */
    @GetMapping("/project/{projectId}")
    public String projectEditor(@PathVariable Long projectId, Authentication auth, Model model) {
        Member member = memberService.findByUsername(auth.getName());
        ProjectDto project = projectService.getMyProject(projectId, member);
        List<FileDto> files = fileParseService.getMyFiles(member);
        model.addAttribute("project", project);
        model.addAttribute("files", files);
        model.addAttribute("username", member.getUsername());
        return "project-editor";
    }

    /**
     * 프로젝트 문서 페이지
     */
    @GetMapping("/docs")
    public String docs(Authentication auth, Model model) {
        Member member = memberService.findByUsername(auth.getName());
        model.addAttribute("username", member.getUsername());
        return "docs";
    }

    /**
     * 공개 페이지 — UUID로 접근 (생성된 HTML을 직접 반환)
     */
    @GetMapping("/page/{uuid}")
    public void publicPage(@PathVariable String uuid, HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try {
            HtmlPage page = pageService.getPublicPage(uuid);
            response.getWriter().write(page.getHtmlContent());
        } catch (Exception e) {
            response.setStatus(404);
            response.getWriter().write(
                "<!DOCTYPE html><html lang='ko'><head><meta charset='UTF-8'>" +
                "<title>404</title><link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'></head>" +
                "<body class='d-flex align-items-center justify-content-center vh-100'>" +
                "<div class='text-center'><h1 class='display-1 text-muted'>404</h1>" +
                "<p class='text-muted'>페이지를 찾을 수 없습니다.</p>" +
                "<a href='/' class='btn btn-primary'>홈으로</a></div></body></html>"
            );
        }
    }
}
