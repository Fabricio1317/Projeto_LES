package com.games.vendas.adapter.out.persistence.repository;

import com.games.vendas.domain.entity.PedidoItem;
import com.games.vendas.domain.enums.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PedidoItemRepository extends JpaRepository<PedidoItem, Long> {

    /** RN0028 — itens de compras ainda EM PROCESSAMENTO seguem reservados, sem baixa no estoque. */
    @Query("""
        SELECT COALESCE(SUM(i.quantidade), 0) FROM PedidoItem i
        WHERE i.jogo.id = :jogoId AND i.pedido.status = :status
        """)
    long somarReservado(@Param("jogoId") Long jogoId, @Param("status") StatusPedido status);

    /**
     * RF0055 — valor vendido (preço × quantidade dos itens) por categoria e mês da compra, somente das
     * compras no status informado e com data de compra em [inicio, fim). Cada linha: categoria, ano, mês, valor.
     */
    @Query("""
        SELECT i.jogo.genero, YEAR(p.dataCompra), MONTH(p.dataCompra), SUM(i.precoUnitario * i.quantidade)
        FROM PedidoItem i JOIN i.pedido p
        WHERE p.status = :status AND p.dataCompra >= :inicio AND p.dataCompra < :fim
          AND i.jogo.genero IN :categorias
        GROUP BY i.jogo.genero, YEAR(p.dataCompra), MONTH(p.dataCompra)
        """)
    List<Object[]> somarVendasPorCategoriaEMes(@Param("status") StatusPedido status,
                                                @Param("inicio") LocalDateTime inicio,
                                                @Param("fim") LocalDateTime fim,
                                                @Param("categorias") Collection<String> categorias);
}
