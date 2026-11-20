package com.games.recomendacao.adapter.out.persistence.repository;

import com.games.recomendacao.domain.entity.PreferenciaCliente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferenciaClienteRepository extends JpaRepository<PreferenciaCliente, Long> {
}
