package com.games.cliente.application.exception;

/**
 * Lançada quando uma regra de negócio (RN) ou requisito não funcional (RNF)
 * de validação de dados do cliente é violado. O campo {@code codigo}
 * identifica qual RN/RNF foi violada, permitindo que os testes automatizados
 * (e a apresentação) associem cada erro à regra correspondente do DRS.
 */
public class RegraNegocioException extends RuntimeException {

    private final String codigoRegra;

    public RegraNegocioException(String codigoRegra, String mensagem) {
        super(mensagem);
        this.codigoRegra = codigoRegra;
    }

    public String getCodigoRegra() {
        return codigoRegra;
    }
}
