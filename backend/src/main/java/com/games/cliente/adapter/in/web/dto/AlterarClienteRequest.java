package com.games.cliente.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RF0022 — alteração de dados cadastrais. Não inclui CPF (documento não é
 * alterável) nem senha (alterada isoladamente via RF0028).
 */
public record AlterarClienteRequest(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        @NotBlank(message = "Telefone é obrigatório")
        String telefone,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail em formato inválido")
        String email,

        @NotBlank(message = "Tipo de residência é obrigatório")
        String enderecoTipoResidencia,

        @NotBlank(message = "Tipo de logradouro é obrigatório")
        String enderecoTipoLogradouro,

        @NotBlank(message = "Logradouro do endereço residencial é obrigatório")
        String enderecoLogradouro,

        @NotBlank(message = "Número do endereço residencial é obrigatório")
        String enderecoNumero,

        @NotBlank(message = "Bairro do endereço residencial é obrigatório")
        String enderecoBairro,

        @NotBlank(message = "CEP do endereço residencial é obrigatório")
        String enderecoCep,

        @NotBlank(message = "Cidade do endereço residencial é obrigatória")
        String enderecoCidade,

        @NotBlank(message = "Estado do endereço residencial é obrigatório")
        @Size(min = 2, max = 2, message = "Estado deve ser a sigla com 2 letras")
        String enderecoEstado,

        @NotBlank(message = "País é obrigatório")
        String pais
) {}