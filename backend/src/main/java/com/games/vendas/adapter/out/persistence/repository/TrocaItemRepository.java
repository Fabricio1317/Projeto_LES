package com.games.vendas.adapter.out.persistence.repository;

import com.games.vendas.domain.entity.TrocaItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrocaItemRepository extends JpaRepository<TrocaItem, Long> {

    /** Quantidade de um item da compra que já está em algum pedido de troca. */
    @Query("SELECT COALESCE(SUM(t.quantidade), 0) FROM TrocaItem t WHERE t.pedidoItem.id = :pedidoItemId")
    long somarQuantidadeEmTroca(@Param("pedidoItemId") Long pedidoItemId);
}
