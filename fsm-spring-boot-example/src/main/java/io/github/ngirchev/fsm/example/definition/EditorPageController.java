package io.github.ngirchev.fsm.example.definition;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EditorPageController {
    @GetMapping("/fsm-editor")
    public String redirect() {
        return "redirect:/fsm-editor/";
    }

    @GetMapping("/fsm-editor/")
    public String editor() {
        return "forward:/fsm-editor/index.html";
    }
}
