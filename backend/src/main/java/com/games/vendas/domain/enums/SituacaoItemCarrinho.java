package com.games.vendas.domain.enums;

/**
 * ATIVO: item no carrinho, com o estoque bloqueado para o cliente (RN0044).
 * REMOVIDO_PRAZO: item retirado porque o prazo de bloqueio expirou; continua
 * listado no carrinho até ser adicionado novamente ou descartado (RNF0042).
 */
public enum SituacaoItemCarrinho {
    ATIVO,
    REMOVIDO_PRAZO
}
