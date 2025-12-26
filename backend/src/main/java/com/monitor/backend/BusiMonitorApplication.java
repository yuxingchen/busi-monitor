package com.monitor.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.monitor.backend.mapper")
public class BusiMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusiMonitorApplication.class, args);
    }
}
