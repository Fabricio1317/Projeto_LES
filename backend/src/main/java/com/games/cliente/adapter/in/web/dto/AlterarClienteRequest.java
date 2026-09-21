package com.games.cliente.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * RF0022 — alteração de dados cadastrais. Não inclui CPF (documento não é
 * alterável) nem senha (alterada isoladamente via RF0028).
 */
public record AlterarClienteRequest(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        // --- Telefone (RN0026: tipo, DDD e número) ---
        @NotBlank(message = "Tipo de telefone é obrigatório")
        String telefoneTipo,

        @NotBlank(message = "DDD do telefone é obrigatório")
        @Pattern(regexp = "\\d{2}", message = "DDD deve ter 2 dígitos")
        String telefoneDdd,

        @NotBlank(message = "Número do telefone é obrigatório")
        @Pattern(regexp = "\\d{8,9}", message = "Número do telefone deve ter 8 ou 9 dígitos")
        String telefoneNumero,
        // ----------------------------------------------

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