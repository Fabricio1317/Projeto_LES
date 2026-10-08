package com.games.vendas.adapter.in.web.dto.response;

import java.math.BigDecimal;

/** RF0034 — valor do frete calculado para a UF de entrega. */
public record FreteResponse(String estado, BigDecimal valorFrete) {}
