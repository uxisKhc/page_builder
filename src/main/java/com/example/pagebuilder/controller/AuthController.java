package com.example.pagebuilder.controller;

import com.example.pagebuilder.service.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    @Autowired
    private MemberService memberService;

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                            @RequestParam(required = false) String logout,
                            Model model) {
        if (error != null) {
            model.addAttribute("error", "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        if (logout != null) {
            model.addAttribute("message", "로그아웃 되었습니다.");
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String email,
                           @RequestParam String password,
                           @RequestParam String passwordConfirm,
                           RedirectAttributes redirectAttributes) {
        try {
            if (!password.equals(passwordConfirm)) {
                redirectAttributes.addFlashAttribute("error", "비밀번호가 일치하지 않습니다.");
                return "redirect:/register";
            }
            if (password.length() < 6) {
                redirectAttributes.addFlashAttribute("error", "비밀번호는 6자 이상이어야 합니다.");
                return "redirect:/register";
            }
            memberService.register(username, email, password);
            redirectAttributes.addFlashAttribute("message", "회원가입이 완료되었습니다. 로그인해주세요.");
            return "redirect:/login";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";
    }
}
