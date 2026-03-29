package org.Aayush.app;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards the embedded TARO SPA routes to the packaged frontend entrypoint.
 */
@Controller
public class FrontendEmbeddingController {
    @GetMapping({"/", "/admin", "/query"})
    public String index() {
        return "forward:/index.html";
    }
}
