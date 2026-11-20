package com.games.recomendacao.config;

import com.games.recomendacao.adapter.in.web.interceptor.PerfilClienteInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RecomendacaoWebConfig implements WebMvcConfigurer {

    private final PerfilClienteInterceptor perfilCliente;

    public RecomendacaoWebConfig(PerfilClienteInterceptor perfilCliente) {
        this.perfilCliente = perfilCliente;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(perfilCliente).addPathPatterns(
                "/api/assistente/**", "/api/clientes/*/assistente/**", "/api/clientes/*/preferencias");
    }
}
