package com.games.vendas.domain.entity;

import com.games.vendas.domain.enums.SituacaoItemCarrinho;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RF0031 — repositório temporário de itens para futura compra.
 * RN0044 — o bloqueio dos itens expira em {@code expiraEm}, calculado a partir
 * do último item incluído no carrinho somado ao prazo parametrizado.
 */
@Entity
@Table(name = "carrinho", uniqueConstraints = {
        @UniqueConstraint(name = "uk_carrinho_cliente", columnNames = "cliente_id")
})
public class Carrinho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "expira_em")
    private LocalDateTime expiraEm;

    @OneToMany(mappedBy = "carrinho", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CarrinhoItem> itens = new ArrayList<>();

    protected Carrinho() {}

    public Carrinho(Long clienteId) {
        this.clienteId = clienteId;
    }

    public List<CarrinhoItem> getItensAtivos() {
        return itens.stream().filter(i -> i.getSituacao() == SituacaoItemCarrinho.ATIVO).toList();
    }

    public List<CarrinhoItem> getItensRemovidos() {
        return itens.stream().filter(i -> i.getSituacao() == SituacaoItemCarrinho.REMOVIDO_PRAZO).toList();
    }

    public Optional<CarrinhoItem> buscarItemDoJogo(Long jogoId) {
        return itens.stream().filter(i -> i.getJogo().getId().equals(jogoId)).findFirst();
    }

    public Optional<CarrinhoItem> buscarItem(Long itemId) {
        return itens.stream().filter(i -> itemId.equals(i.getId())).findFirst();
    }

    public CarrinhoItem adicionarItem(Jogo jogo, int quantidade) {
        CarrinhoItem item = new CarrinhoItem(this, jogo, quantidade);
        itens.add(item);
        return item;
    }

    public void removerItem(CarrinhoItem item) {
        itens.remove(item);
    }

    /** RN0044 — o prazo é sempre relativo ao último item incluído. */
    public void renovarBloqueio(LocalDateTime novoPrazo) {
        this.expiraEm = novoPrazo;
    }

    public boolean isExpirado(LocalDateTime agora) {
        return expiraEm != null && !agora.isBefore(expiraEm) && !getItensAtivos().isEmpty();
    }

    /** RN0044 / RN0045 — ao expirar, todos os itens são desbloqueados e retirados do carrinho. */
    public List<CarrinhoItem> expirarBloqueio() {
        List<CarrinhoItem> expirados = getItensAtivos();
        expirados.forEach(CarrinhoItem::marcarRemovidoPorPrazo);
        this.expiraEm = null;
        return expirados;
    }

    /** Após a finalização da compra, os itens ativos passam a pertencer ao pedido. */
    public void esvaziarItensAtivos() {
        itens.removeIf(i -> i.getSituacao() == SituacaoItemCarrinho.ATIVO);
        this.expiraEm = null;
    }

    public Long getId() { return id; }
    public Long getClienteId() { return clienteId; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
    public List<CarrinhoItem> getItens() { return itens; }
}
