package com.example.pagebuilder.service;

import com.example.pagebuilder.dto.ProjectDto;
import com.example.pagebuilder.entity.HtmlPage;
import com.example.pagebuilder.entity.Member;
import com.example.pagebuilder.entity.Project;
import com.example.pagebuilder.repository.HtmlPageRepository;
import com.example.pagebuilder.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectService {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private HtmlPageRepository pageRepository;

    /** 프로젝트 생성 */
    public ProjectDto createProject(String name, String description, Member member) {
        Project project = new Project();
        project.setMember(member);
        project.setName(name != null && !name.isBlank() ? name : "새 프로젝트");
        project.setDescription(description);
        return ProjectDto.from(projectRepository.save(project));
    }

    /** 내 프로젝트 목록 */
    @Transactional(readOnly = true)
    public List<ProjectDto> getMyProjects(Member member) {
        return projectRepository.findByMemberOrderByCreatedAtDesc(member)
                .stream().map(ProjectDto::from).collect(Collectors.toList());
    }

    /** 프로젝트 상세 (소유자 확인) */
    @Transactional(readOnly = true)
    public ProjectDto getMyProject(Long projectId, Member member) {
        Project project = findOwned(projectId, member);
        return ProjectDto.from(project);
    }

    /** 프로젝트 이름/설명 수정 */
    public ProjectDto updateProject(Long projectId, String name, String description, Member member) {
        Project project = findOwned(projectId, member);
        if (name != null && !name.isBlank()) project.setName(name);
        project.setDescription(description);
        return ProjectDto.from(projectRepository.save(project));
    }

    /** 프로젝트 삭제 (모든 페이지 포함) */
    public void deleteProject(Long projectId, Member member) {
        projectRepository.delete(findOwned(projectId, member));
    }

    /** 프로젝트에 새 페이지 추가 */
    public ProjectDto addPage(Long projectId, String pageName, Member member) {
        Project project = findOwned(projectId, member);
        HtmlPage page = new HtmlPage();
        page.setMember(member);
        page.setProject(project);
        page.setPageName(pageName != null && !pageName.isBlank() ? pageName : "페이지 " + (project.getPages().size() + 1));
        page.setTitle(page.getPageName());
        page.setHtmlContent("<!-- 빈 페이지 -->");
        pageRepository.save(page);
        // 저장 후 최신 상태 반환
        return ProjectDto.from(projectRepository.findByIdAndMember(projectId, member).orElseThrow());
    }

    /** 프로젝트에서 페이지 제거 */
    public ProjectDto removePage(Long projectId, Long pageId, Member member) {
        Project project = findOwned(projectId, member);
        HtmlPage page = pageRepository.findByIdAndMember(pageId, member)
                .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다."));
        if (page.getProject() == null || !page.getProject().getId().equals(project.getId()))
            throw new IllegalArgumentException("해당 프로젝트의 페이지가 아닙니다.");
        pageRepository.delete(page);
        return ProjectDto.from(projectRepository.findByIdAndMember(projectId, member).orElseThrow());
    }

    /** 페이지 이름 변경 */
    public void renamePage(Long pageId, String pageName, Member member) {
        HtmlPage page = pageRepository.findByIdAndMember(pageId, member)
                .orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없습니다."));
        page.setPageName(pageName);
        pageRepository.save(page);
    }

    private Project findOwned(Long projectId, Member member) {
        return projectRepository.findByIdAndMember(projectId, member)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));
    }
}
