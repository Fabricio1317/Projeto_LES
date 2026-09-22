package com.games.cliente.adapter.in.web.dto;

import com.games.cliente.domain.enums.TipoEndereco;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** RF0026 / RN0023 — todos os campos obrigatórios, exceto observações. */
public record EnderecoRequest(
        @NotBlank(message = "Apelido do endereço é obrigatório")
        String apelido,

        @NotNull(message = "Tipo do endereço é obrigatório (COBRANCA, ENTREGA ou AMBOS)")
        TipoEndereco tipo,

        @NotBlank(message = "Tipo de residência é obrigatório (ex: Casa, Apartamento)")
        String tipoResidencia,

        @NotBlank(message = "Tipo de logradouro é obrigatório (ex: Rua, Avenida)")
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