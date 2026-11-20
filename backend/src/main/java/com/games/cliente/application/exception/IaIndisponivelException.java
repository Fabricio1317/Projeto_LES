package com.games.cliente.application.exception;

/** RNF0044 — a IA generativa não está configurada ou não respondeu. */
public class IaIndisponivelException extends RuntimeException {
    public IaIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
