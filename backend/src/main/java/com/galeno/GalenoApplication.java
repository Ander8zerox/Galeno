package com.galeno;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.galeno.shared.ConfiguracionProduccion;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class GalenoApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(GalenoApplication.class);
        app.addListeners(new ConfiguracionProduccion());
        app.run(args);
    }
}
