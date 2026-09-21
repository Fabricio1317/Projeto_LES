package com.games.cliente.application.exception;

/** Usada para sub-recursos do cliente (endereço, cartão) que não foram encontrados. */
public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
