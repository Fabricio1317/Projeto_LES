package com.games.vendas.adapter.in.web.interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca uma rota (método) ou um controller inteiro (classe) como restrito ao perfil administrador.
 * Quem barra o acesso é o {@link PerfilAdministradorInterceptor}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SomenteAdministrador {
}
