package com.games.cliente.application.exception;

public class ClienteNaoEncontradoException extends RuntimeException {
    public ClienteNaoEncontradoException(Long id) {
        super("Cliente não encontrado: id=" + id);
    }
}
