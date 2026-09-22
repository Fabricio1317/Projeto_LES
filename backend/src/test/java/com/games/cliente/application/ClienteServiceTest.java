package com.games.cliente.application;

import com.games.cliente.adapter.in.web.dto.CadastrarClienteRequest;
import com.games.cliente.adapter.out.persistence.ClienteRepository;
import com.games.cliente.adapter.out.persistence.EnderecoRepository;
import com.games.cliente.application.exception.RegraNegocioException;
import com.games.cliente.domain.entity.Cliente;
import com.games.cliente.domain.enums.GeneroCliente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Testes unitários das regras de negócio do ClienteService.
 * Complementares aos testes automatizados de interface (Cypress), que são
 * o foco desta entrega e cobrem os mesmos RF/RN a partir da UI.
 */
@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository repository;

    @Mock
    private AuditoriaService auditoria;

    @Mock
    private EnderecoRepository enderecoRepository;

    @InjectMocks
    private ClienteService service;

    private CadastrarClienteRequest requestValido;

    @BeforeEach
    void setUp() {
        requestValido = new CadastrarClienteRequest(
                GeneroCliente.FEMININO, "Maria Silva", LocalDate.of(1995, 5, 20),
                "529.982.247-25", "Celular", "11", "999998888", "maria@example.com",
                "Senha@123", "Senha@123",
                "Casa", "Rua",
                "Rua das Flores", "100", "Centro", "01310100", "São Paulo", "SP", "Brasil"
        );
        when(repository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void deveCadastrarClienteComDadosValidos() {
        Cliente cliente = service.cadastrar(requestValido);

        assertThat(cliente.getNome()).isEqualTo("Maria Silva");
        assertThat(cliente.getCpf()).isEqualTo("52998224725");
        assertThat(cliente.getRanking()).isZero();
        assertThat(cliente.isAtivo()).isTrue();
        assertThat(cliente.getCodigoCliente()).startsWith("CLI-");
        assertThat(cliente.getSenhaHash()).isNotEqualTo("Senha@123"); // RNF0033: nunca em texto puro
    }

    @Test
    void naoDeveCadastrarComCpfInvalido() {
        var req = new CadastrarClienteRequest(
                GeneroCliente.FEMININO, "Maria Silva", LocalDate.of(1995, 5, 20),
                "111.111.111-11", "Celular", "11", "999998888", "maria@example.com",
                "Senha@123", "Senha@123",
                "Casa", "Rua",
                "Rua das Flores", "100", "Centro", "01310100", "São Paulo", "SP", "Brasil");

        assertThatThrownBy(() -> service.cadastrar(req))
                .isInstanceOf(RegraNegocioException.class)
                .satisfies(ex -> assertThat(((RegraNegocioException) ex).getCodigoRegra()).isEqualTo("CPF_INVALIDO"));
    }

    @Test
    void naoDeveCadastrarComSenhaFraca() {
        var req = new CadastrarClienteRequest(
                GeneroCliente.FEMININO, "Maria Silva", LocalDate.of(1995, 5, 20),
                "529.982.247-25", "Celular", "11", "999998888", "maria@example.com",
                "12345678", "12345678",
                "Casa", "Rua",
                "Rua das Flores", "100", "Centro", "01310100", "São Paulo", "SP", "Brasil");

        assertThatThrownBy(() -> service.cadastrar(req))
                .isInstanceOf(RegraNegocioException.class)
                .satisfies(ex -> assertThat(((RegraNegocioException) ex).getCodigoRegra()).isEqualTo("RNF0031"));
    }

    @Test
    void naoDeveCadastrarComConfirmacaoDeSenhaDivergente() {
        var req = new CadastrarClienteRequest(
                GeneroCliente.FEMININO, "Maria Silva", LocalDate.of(1995, 5, 20),
                "529.982.247-25", "Celular", "11", "999998888", "maria@example.com",
                "Senha@123", "Senha@456",
                "Casa", "Rua",
                "Rua das Flores", "100", "Centro", "01310100", "São Paulo", "SP", "Brasil");

        assertThatThrownBy(() -> service.cadastrar(req))
                .isInstanceOf(RegraNegocioException.class)
                .satisfies(ex -> assertThat(((RegraNegocioException) ex).getCodigoRegra()).isEqualTo("RNF0032"));
    }

    @Test
    void naoDeveInativarClienteJaInativo() {
        Cliente cliente = service.cadastrar(requestValido);
        cliente.inativar();
        when(repository.findById(any())).thenReturn(java.util.Optional.of(cliente));

        assertThatThrownBy(() -> service.inativar(1L))
                .isInstanceOf(RegraNegocioException.class)
                .satisfies(ex -> assertThat(((RegraNegocioException) ex).getCodigoRegra()).isEqualTo("CLIENTE_JA_INATIVO"));
    }

    @Test
    void inativarNaoDeveExcluirOCliente() {
        Cliente cliente = service.cadastrar(requestValido);
        when(repository.findById(any())).thenReturn(java.util.Optional.of(cliente));

        Cliente inativado = service.inativar(1L);

        // o registro continua existindo (nao ha chamada a repository.delete em nenhum ponto do service)
        assertThat(inativado).isNotNull();
        assertThat(inativado.isAtivo()).isFalse();
        assertThat(inativado.getDataInativacao()).isNotNull();
    }
}
