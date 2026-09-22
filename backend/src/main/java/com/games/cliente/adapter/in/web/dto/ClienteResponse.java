package com.games.cliente.adapter.in.web.dto;

import com.games.cliente.domain.entity.Cliente;
import com.games.cliente.domain.enums.GeneroCliente;
import com.games.cliente.domain.enums.StatusCliente;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Representação pública do cliente — a senha (mesmo com hash) nunca é exposta pela API. */
public record ClienteResponse(
        Long id,
        String codigoCliente,
        GeneroCliente genero,
        String nome,
        LocalDate dataNascimento,
        String cpf,
        String telefoneTipo,
        String telefoneDdd,
        String telefoneNumero,
        String email,
        String enderecoTipoResidencia,
        String enderecoTipoLogradouro,
        String enderecoLogradouro,
        String enderecoNumero,
        String enderecoBairro,
        String enderecoCep,
        String enderecoCidade,
        String enderecoEstado,
        String pais,
        Integer ranking,
        StatusCliente status,
        LocalDateTime dataCadastro,
        LocalDateTime dataInativacao
) {
    public static ClienteResponse from(Cliente c) {
        return new ClienteResponse(
                c.getId(), c.getCodigoCliente(), c.getGenero(), c.getNome(), c.getDataNascimento(),
                c.getCpf(), c.getTelefoneTipo(), c.getTelefoneDdd(), c.getTelefoneNumero(), c.getEmail(),
                c.getEnderecoTipoResidencia(), c.getEnderecoTipoLogradouro(),
                c.getEnderecoLogradouro(), c.getEnderecoNumero(), c.getEnderecoBairro(),
                c.getEnderecoCep(), c.getEnderecoCidade(), c.getEnderecoEstado(), c.getPais(),
                c.getRanking(), c.getStatus(), c.getDataCadastro(), c.getDataInativacao()
        );
    }
}
