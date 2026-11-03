package com.games.vendas.domain.enums;

/**
 * Perfis de acesso. Como o sistema não tem autenticação, a tela informa o perfil
 * escolhido no cabeçalho {@code X-Perfil} de cada requisição.
 */
public enum PerfilUsuario {
    CLIENTE,
    ADMINISTRADOR;

    public static final String CABECALHO = "X-Perfil";
}
