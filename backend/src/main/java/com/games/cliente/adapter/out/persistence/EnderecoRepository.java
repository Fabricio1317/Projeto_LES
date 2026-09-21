package com.games.cliente.adapter.out.persistence;

import com.games.cliente.domain.Endereco;
import com.games.cliente.domain.TipoEndereco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnderecoRepository extends JpaRepository<Endereco, Long> {
    List<Endereco> findByClienteIdOrderByIdAsc(Long clienteId);
    long countByClienteIdAndTipo(Long clienteId, TipoEndereco tipo);
}
