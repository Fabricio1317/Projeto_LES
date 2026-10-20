package com.games.vendas.application.event;

/** Publicado ao finalizar uma compra (RF0037); dispara a validação automática do pagamento. */
public record CompraFinalizadaEvent(Long pedidoId) {}
