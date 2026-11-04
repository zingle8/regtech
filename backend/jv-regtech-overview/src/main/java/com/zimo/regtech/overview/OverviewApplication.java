package com.zimo.regtech.overview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.zimo.regtech")
@MapperScan("com.zimo.regtech.overview.mapper")
public class OverviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(OverviewApplication.class, args);
    }
}
