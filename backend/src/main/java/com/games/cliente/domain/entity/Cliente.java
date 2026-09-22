package com.games.cliente.domain.entity;

import com.games.cliente.domain.enums.GeneroCliente;
import com.games.cliente.domain.enums.StatusCliente;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidade de domínio / persistência do Cliente.
 *
 * Campos obrigatórios de cadastro conforme RN0026 do DRS: gênero, nome,
 * data de nascimento, CPF, telefone (tipo, DDD e número), e-mail, senha e
 * endereço residencial.
 */
@Entity
@Table(name = "cliente", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cliente_cpf", columnNames = "cpf"),
        @UniqueConstraint(name = "uk_cliente_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_cliente_codigo", columnNames = "codigo_cliente")
}, indexes = {
        @Index(name = "idx_cliente_nome", columnList = "nome"),
        @Index(name = "idx_cliente_status", columnList = "status")
})
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_cliente", nullable = false, updatable = false, length = 20)
    private String codigoCliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GeneroCliente genero;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Column(nullable = false, length = 11)
    private String cpf;

    // --- Telefone (RN0026: tipo, DDD e número) ---
    @Column(name = "telefone_tipo", nullable = false, length = 30)
    private String telefoneTipo;

    @Column(name = "telefone_ddd", nullable = false, length = 2)
    private String telefoneDdd;

    @Column(name = "telefone_numero", nullable = false, length = 9)
    private String telefoneNumero;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    // --- Endereço residencial completo (RN0023 / RN0026) ---
    @Column(name = "endereco_tipo_residencia", nullable = false, length = 50)
    private String enderecoTipoResidencia;

    @Column(name = "endereco_tipo_logradouro", nullable = false, length = 30)
    private String enderecoTipoLogradouro;

    @Column(name = "endereco_logradouro", nullable = false, length = 150)
    private String enderecoLogradouro;

    @Column(name = "endereco_numero", nullable = false, length = 20)
    private String enderecoNumero;

    @Column(name = "endereco_bairro", nullable = false, length = 100)
    private String enderecoBairro;

    @Column(name = "endereco_cep", nullable = false, length = 8)
    private String enderecoCep;

    @Column(name = "endereco_cidade", nullable = false, length = 100)
    private String enderecoCidade;

    @Column(name = "endereco_estado", nullable = false, length = 2)
    private String enderecoEstado;

    @Column(nullable = false, length = 50)
    private String pais;

    @Column(nullable = false)
    private Integer ranking = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusCliente status = StatusCliente.ATIVO;

    @Column(name = "data_cadastro", nullable = false)
    private LocalDateTime dataCadastro;

    @Column(name = "data_inativacao")
    private LocalDateTime dataInativacao;

    protected Cliente() {}

    public Cliente(String codigoCliente, GeneroCliente genero, String nome, LocalDate dataNascimento,
                   String cpf, String telefoneTipo, String telefoneDdd, String telefoneNumero,
                   String email, String senhaHash,
                   String enderecoTipoResidencia, String enderecoTipoLogradouro,
                   String enderecoLogradouro, String enderecoNumero, String enderecoBairro,
                   String enderecoCep, String enderecoCidade, String enderecoEstado, String pais) {
        this.codigoCliente = codigoCliente;
        this.genero = genero;
        this.nome = nome;
        this.dataNascimento = dataNascimento;
        this.cpf = cpf;
        this.telefoneTipo = telefoneTipo;
        this.telefoneDdd = telefoneDdd;
        this.telefoneNumero = telefoneNumero;
        this.email = email;
        this.senhaHash = senhaHash;
        this.enderecoTipoResidencia = enderecoTipoResidencia;
        this.enderecoTipoLogradouro = enderecoTipoLogradouro;
        this.enderecoLogradouro = enderecoLogradouro;
        this.enderecoNumero = enderecoNumero;
        this.enderecoBairro = enderecoBairro;
        this.enderecoCep = enderecoCep;
        this.enderecoCidade = enderecoCidade;
        this.enderecoEstado = enderecoEstado;
        this.pais = pais;
        this.ranking = 0;
        this.status = StatusCliente.ATIVO;
        this.dataCadastro = LocalDateTime.now();
    }

    public void inativar() {
        this.status = StatusCliente.INATIVO;
        this.dataInativacao = LocalDateTime.now();
    }

    public void reativar() {
        this.status = StatusCliente.ATIVO;
        this.dataInativacao = null;
    }

    public boolean isAtivo() {
        return this.status == StatusCliente.ATIVO;
    }

    public void atualizarDados(String nome, String telefoneTipo, String telefoneDdd, String telefoneNumero, String email,
                               String enderecoTipoResidencia, String enderecoTipoLogradouro,
                               String enderecoLogradouro, String enderecoNumero, String enderecoBairro,
                               String enderecoCep, String enderecoCidade, String enderecoEstado, String pais) {
        this.nome = nome;
        this.telefoneTipo = telefoneTipo;
        this.telefoneDdd = telefoneDdd;
        this.telefoneNumero = telefoneNumero;
        this.email = email;
        this.enderecoTipoResidencia = enderecoTipoResidencia;
        this.enderecoTipoLogradouro = enderecoTipoLogradouro;
        this.enderecoLogradouro = enderecoLogradouro;
        this.enderecoNumero = enderecoNumero;
        this.enderecoBairro = enderecoBairro;
        this.enderecoCep = enderecoCep;
        this.enderecoCidade = enderecoCidade;
        this.enderecoEstado = enderecoEstado;
        this.pais = pais;
    }

    public void atualizarSenhaHash(String novaSenhaHash) {
        this.senhaHash = novaSenhaHash;
    }

    public void atualizarRanking(int novoRanking) {
        this.ranking = novoRanking;
    }

    // --- getters ---
    public Long getId() { return id; }
    public String getCodigoCliente() { return codigoCliente; }
    public GeneroCliente getGenero() { return genero; }
    public String getNome() { return nome; }
    public LocalDate getDataNascimento() { return dataNascimento; }
    public String getCpf() { return cpf; }
    public String getTelefoneTipo() { return telefoneTipo; }
    public String getTelefoneDdd() { return telefoneDdd; }
    public String getTelefoneNumero() { return telefoneNumero; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public String getEnderecoTipoResidencia() { return enderecoTipoResidencia; }
    public String getEnderecoTipoLogradouro() { return enderecoTipoLogradouro; }
    public String getEnderecoLogradouro() { return enderecoLogradouro; }
    public String getEnderecoNumero() { return enderecoNumero; }
    public String getEnderecoBairro() { return enderecoBairro; }
    public String getEnderecoCep() { return enderecoCep; }
    public String getEnderecoCidade() { return enderecoCidade; }
    public String getEnderecoEstado() { return enderecoEstado; }
    public String getPais() { return pais; }
    public Integer getRanking() { return ranking; }
    public StatusCliente getStatus() { return status; }
    public LocalDateTime getDataCadastro() { return dataCadastro; }
    public LocalDateTime getDataInativacao() { return dataInativacao; }
}