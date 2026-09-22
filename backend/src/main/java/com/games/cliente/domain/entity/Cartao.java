package com.games.cliente.domain.entity;

import com.games.cliente.domain.enums.Bandeira;
import jakarta.persistence.*;

/**
 * Cartão de crédito cadastrado pelo cliente (RF0027). Um cliente pode ter
 * múltiplos cartões, com no máximo um marcado como preferencial.
 *
 * Nota de segurança: em um sistema real de produção, número e código de
 * segurança nunca seriam armazenados em texto puro (isso violaria PCI-DSS);
 * o correto seria tokenizar via um gateway de pagamento. Aqui são guardados
 * apenas para fins acadêmicos, refletindo fielmente os campos pedidos no
 * DRS (RN0024).
 */
@Entity
@Table(name = "cartao", indexes = {
        @Index(name = "idx_cartao_cliente_id", columnList = "cliente_id")
})
public class Cartao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    // --- composição do cartão (RN0024) ---
    @Column(nullable = false, length = 19)
    private String numero;

    @Column(name = "nome_impresso", nullable = false, length = 100)
    private String nomeImpresso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Bandeira bandeira;

    @Column(name = "codigo_seguranca", nullable = false, length = 4)
    private String codigoSeguranca;

    @Column(nullable = false)
    private boolean preferencial;

    protected Cartao() {}

    public Cartao(Long clienteId, String numero, String nomeImpresso, Bandeira bandeira,
                 String codigoSeguranca, boolean preferencial) {
        this.clienteId = clienteId;
        this.numero = numero;
        this.nomeImpresso = nomeImpresso;
        this.bandeira = bandeira;
        this.codigoSeguranca = codigoSeguranca;
        this.preferencial = preferencial;
    }

    public void marcarComoPreferencial() { this.preferencial = true; }
    public void desmarcarComoPreferencial() { this.preferencial = false; }

    public Long getId() { return id; }
    public Long getClienteId() { return clienteId; }
    public String getNumero() { return numero; }
    public String getNomeImpresso() { return nomeImpresso; }
    public Bandeira getBandeira() { return bandeira; }
    public String getCodigoSeguranca() { return codigoSeguranca; }
    public boolean isPreferencial() { return preferencial; }

    /** Últimos 4 dígitos, para exibição segura na interface (nunca o número completo). */
    public String getNumeroMascarado() {
        if (numero == null || numero.length() < 4) return "****";
        return "**** **** **** " + numero.substring(numero.length() - 4);
    }
}
