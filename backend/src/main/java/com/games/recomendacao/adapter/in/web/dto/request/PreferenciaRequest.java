package com.games.recomendacao.adapter.in.web.dto.request;

import java.math.BigDecimal;
import java.util.List;

/** Preferências que o cliente marca no perfil (gêneros, plataformas, orçamento por jogo e observações). */
public record PreferenciaRequest(List<String> generos, List<String> plataformas, BigDecimal orcamentoMaximo, String observacoes) {
}
