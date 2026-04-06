package org.Aayush.testsupport;

import org.Aayush.api.FutureApiTestConfiguration;
import org.Aayush.app.Main;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(
        classes = {Main.class, FutureApiTestConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "taro.demo-topology.enabled=false")
@AutoConfigureMockMvc
public @interface TaroApiSpringTest {
}
