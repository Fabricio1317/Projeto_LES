package com.games.vendas.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RF0035 — novo endereço de entrega informado na compra. Segue a composição
 * da RN0023 e o apelido (frase curta de identificação) do RF0026.
 */
public record NovoEnderecoEntregaRequest(
        @NotBlank(message = "Apelido do endereço é obrigatório")
        String apelido,

        @NotBlank(message = "Tipo de residência é obrigatório")
        String tipoResidencia,

        @NotBlank(message = "Tipo de logradouro é obrigatório")
        String tipoLogradouro,

        @NotBlank(message = "Logradouro é obrigatório")
        String logradouro,

        @NotBlank(message = "Número é obrigatório")
        String numero,

        @NotBlank(message = "Bairro é obrigatório")
        String bairro,

        @NotBlank(message = "CEP é obrigatório")
        String cep,

        @NotBlank(message = "Cidade é obrigatória")
        String cidade,

        @NotBlank(message = "Estado é obrigatório")
        @Size(min = 2, max = 2, message = "Estado deve ser a sigla com 2 letras")
        String estado,

        @NotBlank(message = "País é obrigatório")
        String pais,

        String observacoes
) {}
