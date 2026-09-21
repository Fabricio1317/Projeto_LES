package com.games.cliente.adapter.in.web.dto;

import com.games.cliente.domain.Endereco;
import com.games.cliente.domain.TipoEndereco;

public record EnderecoResponse(
        Long id, Long clienteId, String apelido, TipoEndereco tipo,
        String tipoResidencia, String tipoLogradouro, String logradouro,
        String numero, String bairro, String cep, String cidade,
        String estado, String pais, String observacoes
) {
    public static EnderecoResponse from(Endereco e) {
        return new EnderecoResponse(
                e.getId(), e.getClienteId(), e.getApelido(), e.getTipo(),
                e.getTipoResidencia(), e.getTipoLogradouro(), e.getLogradouro(),
                e.getNumero(), e.getBairro(), e.getCep(), e.getCidade(),
                e.getEstado(), e.getPais(), e.getObservacoes()
        );
    }
}