package com.games.cliente.adapter.in.web.dto;

import com.games.cliente.domain.enums.GeneroCliente;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

/**
 * RF0021 / RN0026 — todos os campos são obrigatórios para o cadastro,
 * incluindo o endereço residencial completo (RN0023) e a confirmação de senha (RNF0032).
 */
public record CadastrarClienteRequest(
        @NotNull(message = "Gênero é obrigatório")
        GeneroCliente genero,

        @NotBlank(message = "Nome é obrigatório")
        String nome,

        @NotNull(message = "Data de nascimento é obrigatória")
        @Past(message = "Data de nascimento deve estar no passado")
        LocalDate dataNascimento,

        @NotBlank(message = "CPF é obrigatório")
        String cpf,

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

        @NotBlank(message = "Senha é obrigatória")
        String senha,

        @NotBlank(message = "Confirmação de senha é obrigatória")
        String confirmacaoSenha,

        // --- Novos campos da RN0023 ---
        @NotBlank(message = "Tipo de residência é obrigatório")
        String enderecoTipoResidencia,

        @NotBlank(message = "Tipo de logradouro é obrigatório")
        String enderecoTipoLogradouro,
        // ------------------------------

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

        String pais
) {}