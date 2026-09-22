package io.github.ngirchev.fsm.example;

import io.github.ngirchev.dotenv.DotEnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Path;

@SpringBootApplication
public class FsmExampleApplication {
    // bootRun or the executable jar starts Spring, Flyway and the starter's background worker.
    public static void main(String[] args) {
        DotEnvLoader.loadDotEnv();
        // bootRun starts in the module directory; the shared .env is in the repository root.
        DotEnvLoader.loadDotEnv(Path.of("..", ".env"));
        SpringApplication.run(FsmExampleApplication.class, args);
    }
}
