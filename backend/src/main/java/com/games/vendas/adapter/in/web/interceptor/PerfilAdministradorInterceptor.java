package com.games.vendas.adapter.in.web.interceptor;

import com.games.cliente.application.exception.AcessoNegadoException;
import com.games.vendas.domain.enums.PerfilUsuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Libera a análise de vendas (RF0055 / RNF0043) somente para o perfil administrador. */
@Component
public class PerfilAdministradorInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String perfil = request.getHeader(PerfilUsuario.CABECALHO);
        if (!PerfilUsuario.ADMINISTRADOR.name().equalsIgnoreCase(perfil)) {
            throw new AcessoNegadoException("A análise de vendas é restrita ao perfil administrador.");
        }
        return true;
    }
}
