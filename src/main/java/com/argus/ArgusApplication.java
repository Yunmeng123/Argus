package com.argus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ArgusApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArgusApplication.class, args);
    }
}
