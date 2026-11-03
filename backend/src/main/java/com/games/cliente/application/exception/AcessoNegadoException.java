package com.games.cliente.application.exception;

/** O perfil informado não tem permissão para acessar o recurso. */
public class AcessoNegadoException extends RuntimeException {
    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
