package com.games.recomendacao.adapter.in.web.interceptor;

import com.games.cliente.application.exception.AcessoNegadoException;
import com.games.vendas.domain.enums.PerfilUsuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** O chatbot de recomendação é usado pelo perfil cliente (critério "a" da atividade de RNF0044). */
@Component
public class PerfilClienteInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String perfil = request.getHeader(PerfilUsuario.CABECALHO);
        if (!PerfilUsuario.CLIENTE.name().equalsIgnoreCase(perfil)) {
            throw new AcessoNegadoException("O assistente de recomendação é usado pelo perfil cliente.");
        }
        return true;
    }
}
