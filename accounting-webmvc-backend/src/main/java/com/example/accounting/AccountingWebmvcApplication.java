package com.example.accounting;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.accounting.mapper")
public class AccountingWebmvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountingWebmvcApplication.class, args);
    }
}
