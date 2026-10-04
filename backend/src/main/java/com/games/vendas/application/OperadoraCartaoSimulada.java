package com.games.vendas.application;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Simulação da operadora de cartão de crédito usada na validação do
 * pagamento (RN0037). Convenção para demonstração e testes: cartões cujo
 * número termina em "0000" são recusados; os demais são aprovados.
 */
@Component
public class OperadoraCartaoSimulada {

    public boolean autorizar(String numeroCartao, BigDecimal valor) {
        return !numeroCartao.endsWith("0000");
    }
}
