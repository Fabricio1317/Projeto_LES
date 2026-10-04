package com.games.vendas.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Cópia do endereço de entrega no momento da compra (RF0035). Fica gravada no
 * pedido para que alterações posteriores no perfil do cliente não mudem o
 * histórico da compra. Segue a composição da RN0023.
 */
@Embeddable
public class EnderecoEntrega {

    @Column(name = "entrega_apelido", nullable = false, length = 60)
    private String apelido;

    @Column(name = "entrega_tipo_residencia", nullable = false, length = 50)
    private String tipoResidencia;

    @Column(name = "entrega_tipo_logradouro", nullable = false, length = 30)
    private String tipoLogradouro;

    @Column(name = "entrega_logradouro", nullable = false, length = 150)
    private String logradouro;

    @Column(name = "entrega_numero", nullable = false, length = 20)
    private String numero;

    @Column(name = "entrega_bairro", nullable = false, length = 100)
    private String bairro;

    @Column(name = "entrega_cep", nullable = false, length = 8)
    private String cep;

    @Column(name = "entrega_cidade", nullable = false, length = 100)
    private String cidade;

    @Column(name = "entrega_estado", nullable = false, length = 2)
    private String estado;

    @Column(name = "entrega_pais", nullable = false, length = 50)
    private String pais;

    protected EnderecoEntrega() {}

    public EnderecoEntrega(String apelido, String tipoResidencia, String tipoLogradouro, String logradouro,
                           String numero, String bairro, String cep, String cidade, String estado, String pais) {
        this.apelido = apelido;
        this.tipoResidencia = tipoResidencia;
        this.tipoLogradouro = tipoLogradouro;
        this.logradouro = logradouro;
        this.numero = numero;
        this.bairro = bairro;
        this.cep = cep;
        this.cidade = cidade;
        this.estado = estado;
        this.pais = pais;
    }

    public String resumo() {
        return apelido + " — " + tipoLogradouro + " " + logradouro + ", " + numero + " — " + bairro
                + ", " + cidade + "/" + estado + " — CEP " + cep;
    }

    public String getApelido() { return apelido; }
    public String getTipoResidencia() { return tipoResidencia; }
    public String getTipoLogradouro() { return tipoLogradouro; }
    public String getLogradouro() { return logradouro; }
    public String getNumero() { return numero; }
    public String getBairro() { return bairro; }
    public String getCep() { return cep; }
    public String getCidade() { return cidade; }
    public String getEstado() { return estado; }
    public String getPais() { return pais; }
}
