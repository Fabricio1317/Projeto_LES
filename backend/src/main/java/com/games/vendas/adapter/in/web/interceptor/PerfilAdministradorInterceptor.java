package com.games.vendas.adapter.in.web.interceptor;

import com.games.cliente.application.exception.AcessoNegadoException;
import com.games.vendas.domain.enums.PerfilUsuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Restringe ao perfil administrador a análise de vendas e o treinamento da IA (critérios das atividades;
 * o DRS não numera essas restrições).
 */
@Component
public class PerfilAdministradorInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String perfil = request.getHeader(PerfilUsuario.CABECALHO);
        if (!PerfilUsuario.ADMINISTRADOR.name().equalsIgnoreCase(perfil)) {
            throw new AcessoNegadoException("Recurso restrito ao perfil administrador.");
        }
        return true;
    }
}
