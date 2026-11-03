package com.games.vendas.config;

import com.games.vendas.adapter.in.web.interceptor.PerfilAdministradorInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PerfilAdministradorInterceptor perfilAdministrador;

    public WebConfig(PerfilAdministradorInterceptor perfilAdministrador) {
        this.perfilAdministrador = perfilAdministrador;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(perfilAdministrador).addPathPatterns("/api/analises/**");
    }
}
