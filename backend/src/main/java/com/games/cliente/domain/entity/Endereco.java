package com.games.cliente.domain.entity;

import com.games.cliente.domain.enums.TipoEndereco;
import jakarta.persistence.*;

/**
 * Endereço de entrega/cobrança cadastrado pelo cliente (RF0026).
 * Distinto do "endereço residencial" exigido em RN0026 no cadastro do
 * cliente (Cliente.enderecoLogradouro etc.), que identifica onde o cliente
 * mora. Este é o endereço usado nas compras (RN0021/RN0022/RN0023).
 */
@Entity
@Table(name = "endereco", indexes = {
        @Index(name = "idx_endereco_cliente_id", columnList = "cliente_id")
})
public class Endereco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    /** Nome curto para identificar o endereço (ex.: "Casa", "Trabalho") — RF0026. */
    @Column(nullable = false, length = 60)
    private String apelido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoEndereco tipo;

    // --- composição do endereço (RN0023) ---
    @Column(name = "tipo_residencia", nullable = false, length = 50)
    private String tipoResidencia;

    @Column(name = "tipo_logradouro", nullable = false, length = 30)
    private String tipoLogradouro;

    @Column(nullable = false, length = 150)
    private String logradouro;

    @Column(nullable = false, length = 20)
    private String numero;

    @Column(nullable = false, length = 100)
    private String bairro;

    @Column(nullable = false, length = 8)
    private String cep;

    @Column(nullable = false, length = 100)
    private String cidade;

    @Column(nullable = false, length = 2)
    private String estado;

    @Column(nullable = false, length = 50)
    private String pais;

    @Column(length = 255)
    private String observacoes;

    protected Endereco() {}

    public Endereco(Long clienteId, String apelido, TipoEndereco tipo, String tipoResidencia,
                    String tipoLogradouro, String logradouro, String numero, String bairro,
                    String cep, String cidade, String estado, String pais, String observacoes) {
        this.clienteId = clienteId;
        this.apelido = apelido;
        this.tipo = tipo;
        this.tipoResidencia = tipoResidencia;
        this.tipoLogradouro = tipoLogradouro;
        this.logradouro = logradouro;
        this.numero = numero;
        this.bairro = bairro;
        this.cep = cep;
        this.cidade = cidade;
        this.estado = estado;
        this.pais = pais;
        this.observacoes = observacoes;
    }

    /** RNF0034 — permite alterar este endereço isoladamente, sem tocar em outros dados do cliente. */
    public void atualizar(String apelido, TipoEndereco tipo, String tipoResidencia, String tipoLogradouro,
                          String logradouro, String numero, String bairro, String cep,
                          String cidade, String estado, String pais, String observacoes) {
        this.apelido = apelido;
        this.tipo = tipo;
        this.tipoResidencia = tipoResidencia;
        this.tipoLogradouro = tipoLogradouro;
        this.logradouro = logradouro;
        this.numero = numero;
        this.bairro = bairro;
        this.cep = cep;
        this.cidade = cidade;
        this.estado = estado;
        this.pais = pais;
        this.observacoes = observacoes;
    }

    public Long getId() { return id; }
    public Long getClienteId() { return clienteId; }
    public String getApelido() { return apelido; }
    public TipoEndereco getTipo() { return tipo; }
    public String getTipoResidencia() { return tipoResidencia; }
    public String getTipoLogradouro() { return tipoLogradouro; }
    public String getLogradouro() { return logradouro; }
    public String getNumero() { return numero; }
    public String getBairro() { return bairro; }
    public String getCep() { return cep; }
    public String getCidade() { return cidade; }
    public String getEstado() { return estado; }
    public String getPais() { return pais; }
    public String getObservacoes() { return observacoes; }
}