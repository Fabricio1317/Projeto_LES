package com.games.cliente.adapter.in.web.dto;

import com.games.cliente.domain.GeneroCliente;
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

        @NotBlank(message = "Telefone é obrigatório")
        String telefone,

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