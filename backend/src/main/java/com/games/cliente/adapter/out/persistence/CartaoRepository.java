package com.games.cliente.adapter.out.persistence;

import com.games.cliente.domain.entity.Cartao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CartaoRepository extends JpaRepository<Cartao, Long> {
    List<Cartao> findByClienteIdOrderByIdAsc(Long clienteId);
}
