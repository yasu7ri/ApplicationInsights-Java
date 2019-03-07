package com.microsoft.applicationinsights.testapps.perf.boot;

import com.microsoft.applicationinsights.extensibility.CustomPerfEventTelemetryModule;
import com.microsoft.applicationinsights.extensibility.TelemetryModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class PerfTestAppSpringBoot extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder applicationBuilder) {
        return applicationBuilder.sources(PerfTestAppSpringBoot.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(PerfTestAppSpringBoot.class, args);
    }

    @Bean
    public TelemetryModule customPerfEventModule() {
        return new CustomPerfEventTelemetryModule();
    }

}
