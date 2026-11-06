package org.maslowis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HarnessApp {

    static void main(String[] args) {
        SpringApplication.run(HarnessApp.class, args);
    }
}