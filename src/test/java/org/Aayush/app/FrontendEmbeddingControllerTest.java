package org.Aayush.app;

import org.Aayush.testsupport.AbstractTaroApiSpringTest;
import org.Aayush.testsupport.TaroApiSpringTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TaroApiSpringTest
@Tag("integration")
@DisplayName("Embedded Frontend Routes")
class FrontendEmbeddingControllerTest extends AbstractTaroApiSpringTest {
    @Test
    @DisplayName("Embedded frontend entry routes forward to the packaged SPA index")
    void testEmbeddedFrontendRoutes() throws Exception {
        assertSpaForward("/");
        assertSpaForward("/admin");
        assertSpaForward("/query");
        assertSpaForward("/plan");
        assertSpaForward("/plan/caller-a");

        mockMvc.perform(get("/index.html").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<div id=\"root\"></div>")))
                .andExpect(content().string(containsString("TARO Control Surface")));
    }

    private void assertSpaForward(String path) throws Exception {
        mockMvc.perform(get(path).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}
