package io.github.ngirchev.fsm.example;

import io.github.ngirchev.dotenv.DotEnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@SpringBootApplication
public class FsmExampleApplication implements WebMvcConfigurer {
    // bootRun or the executable jar starts Spring, Flyway and the starter's background worker.
    public static void main(String[] args) {
        DotEnvLoader.loadDotEnv();
        // bootRun starts in the module directory; the shared .env is in the repository root.
        DotEnvLoader.loadDotEnv(Path.of("..", ".env"));
        SpringApplication.run(FsmExampleApplication.class, args);
    }

    // The separately served editor (Compose service `editor`) calls the admin API from another origin.
    // Credentials are allowed because the editor sends cookies by default.
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/fsm-admin/api/**").allowedOrigins("http://localhost:18090")
                .allowedMethods("GET", "POST", "PUT", "DELETE").allowCredentials(true);
    }
}
