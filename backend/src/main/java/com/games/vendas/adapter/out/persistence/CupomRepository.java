package com.games.vendas.adapter.out.persistence;

import com.games.vendas.domain.entity.Cupom;
import com.games.vendas.domain.enums.TipoCupom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CupomRepository extends JpaRepository<Cupom, Long> {

    Optional<Cupom> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);

    List<Cupom> findByClienteIdAndTipoAndUtilizadoFalseOrderByIdAsc(Long clienteId, TipoCupom tipo);

    List<Cupom> findAllByOrderByIdDesc();
}
