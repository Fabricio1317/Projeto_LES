package com.games.cliente.domain.enums;

/**
 * Status do cadastro do cliente.
 *
 * IMPORTANTE (RF0023 do DRS): o sistema trata a remoção de um cliente como
 * INATIVAÇÃO, nunca como exclusão física do registro. Isso preserva a
 * integridade referencial com o histórico de compras do cliente (RF0025) e
 * permite reverter uma inativação feita por engano. Não existe, portanto,
 * nenhuma operação de "hard delete" sobre Cliente neste módulo.
 */
public enum StatusCliente {
    ATIVO,
    INATIVO
}
