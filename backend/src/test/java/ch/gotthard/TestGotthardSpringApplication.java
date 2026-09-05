package ch.gotthard;

import org.springframework.boot.SpringApplication;

public class TestGotthardSpringApplication {

    public static void main(String[] args) {
        SpringApplication.from(GotthardSpringApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
