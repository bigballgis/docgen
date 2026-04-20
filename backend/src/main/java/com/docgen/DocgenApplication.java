package com.docgen;

import com.docgen.service.AuthService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 低代码文档生成平台 - 后端启动类
 */
@SpringBootApplication
public class DocgenApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocgenApplication.class, args);
    }

    @Bean
    public CommandLineRunner initAdminUser(AuthService authService) {
        return args -> {
            authService.initDefaultAdmin();
        };
    }

}
