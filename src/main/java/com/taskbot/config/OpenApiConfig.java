package com.taskbot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI taskBotOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TaskBot API")
                        .description("Telegram bot for managing tasks, meetings, notes, and doctor appointments")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("TaskBot Team")
                                .email("support@taskbot.example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
