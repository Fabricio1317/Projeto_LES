package com.games.cliente.domain;

/**
 * Tipo de um endereço cadastrado pelo cliente (RF0026).
 * RN0021 exige ao menos um endereço de COBRANCA; RN0022 exige ao menos
 * um de ENTREGA. AMBOS satisfaz as duas regras simultaneamente, cobrindo
 * o caso comum de o cliente usar o mesmo endereço para cobrança e entrega.
 */
public enum TipoEndereco {
    COBRANCA,
    ENTREGA,
    AMBOS
}
