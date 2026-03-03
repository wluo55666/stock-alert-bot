package com.weiluo.marketalert;

import com.weiluo.marketalert.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class MarketAlertApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketAlertApplication.class, args);
    }

}
