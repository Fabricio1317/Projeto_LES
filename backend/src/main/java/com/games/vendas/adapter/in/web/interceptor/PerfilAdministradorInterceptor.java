package com.games.vendas.adapter.in.web.interceptor;

import com.games.cliente.application.exception.AcessoNegadoException;
import com.games.vendas.domain.enums.PerfilUsuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Restringe ao perfil administrador as rotas marcadas com {@link SomenteAdministrador}: a análise de vendas,
 * o treinamento da IA e as ações da gestão de vendas (despacho, entrega, trocas, cupons, parâmetros e
 * estoque). O perfil vem do cabeçalho X-Perfil, pois o sistema não tem login.
 */
@Component
public class PerfilAdministradorInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod metodo) || !restrito(metodo)) {
            return true;
        }
        String perfil = request.getHeader(PerfilUsuario.CABECALHO);
        if (!PerfilUsuario.ADMINISTRADOR.name().equalsIgnoreCase(perfil)) {
            throw new AcessoNegadoException("Recurso restrito ao perfil administrador.");
        }
        return true;
    }

    private static boolean restrito(HandlerMethod metodo) {
        return metodo.hasMethodAnnotation(SomenteAdministrador.class)
                || metodo.getBeanType().isAnnotationPresent(SomenteAdministrador.class);
    }
}
