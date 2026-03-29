package com.weiluo.marketalert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@ConfigurationPropertiesScan("com.weiluo.marketalert.config")
public class MarketAlertApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketAlertApplication.class, args);
    }
}
