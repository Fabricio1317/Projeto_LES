package com.games.vendas.adapter.in.web.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/** Cadastro simplificado de jogo para alimentar a loja (o CRUD completo é RF0011–RF0016). */
public record JogoRequest(
        @NotBlank(message = "Título é obrigatório")
        String titulo,

        @NotBlank(message = "Plataforma é obrigatória")
        String plataforma,

        @NotBlank(message = "Gênero é obrigatório")
        String genero,

        @NotBlank(message = "Desenvolvedora é obrigatória")
        String desenvolvedora,

        String distribuidora,

        @NotBlank(message = "Classificação indicativa é obrigatória")
        @Pattern(regexp = "L|10|12|14|16|18", message = "Classificação indicativa deve ser L, 10, 12, 14, 16 ou 18")
        String classificacaoIndicativa,

        Integer ano,

        String codigoBarras,

        @NotNull(message = "Preço é obrigatório")
        @DecimalMin(value = "0.01", message = "Preço deve ser maior que zero")
        BigDecimal preco,

        @NotNull(message = "Estoque é obrigatório")
        @Min(value = 0, message = "Estoque não pode ser negativo")
        Integer estoque,

        @NotNull(message = "Peso é obrigatório")
        @DecimalMin(value = "0.001", message = "Peso deve ser maior que zero")
        BigDecimal pesoKg
) {}
