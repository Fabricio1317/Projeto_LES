package com.games.cliente.application;

import com.games.cliente.adapter.in.web.dto.*;
import com.games.cliente.adapter.out.persistence.ClienteRepository;
import com.games.cliente.adapter.out.persistence.EnderecoRepository;
import com.games.cliente.application.exception.ClienteNaoEncontradoException;
import com.games.cliente.application.exception.RegraNegocioException;
import com.games.cliente.application.validation.CpfValidator;
import com.games.cliente.application.validation.SenhaValidator;
import com.games.cliente.domain.Cliente;
import com.games.cliente.domain.Endereco;
import com.games.cliente.domain.StatusCliente;
import com.games.cliente.domain.TipoEndereco;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;
/**
 * Regras de negócio do módulo de gestão de Clientes (RF0021–RF0028,
 * RN0021–RN0027, RNF0031–RNF0035 do DRS). Cada método documenta, no
 * javadoc, o(s) RF/RN/RNF que implementa — a mesma referência usada nos
 * testes automatizados e na apresentação.
 */
@Service
public class ClienteService {

    private final ClienteRepository repository;
    private final AuditoriaService auditoria;
    private final EnderecoRepository enderecoRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public ClienteService(ClienteRepository repository, AuditoriaService auditoria, EnderecoRepository enderecoRepository) {
        this.repository = repository;
        this.auditoria = auditoria;
        this.enderecoRepository = enderecoRepository;
    }

    /**
     * RF0021 — cadastra um novo cliente.
     * RN0026 — valida todos os campos obrigatórios (feito via @Valid no controller
     * para os campos em branco; aqui são validadas as regras que dependem de lógica,
     * não apenas de presença do campo).
     * RNF0031 — exige senha forte.
     * RNF0032 — exige confirmação de senha.
     * RNF0033 — armazena apenas o hash da senha.
     * RNF0035 — gera código único de cliente.
     */
    @Transactional
    public Cliente cadastrar(CadastrarClienteRequest req) {
        String cpfLimpo = CpfValidator.somenteDigitos(req.cpf());

        if (!CpfValidator.isValid(cpfLimpo)) {
            throw new RegraNegocioException("CPF_INVALIDO", "CPF inválido.");
        }
        if (repository.existsByCpf(cpfLimpo)) {
            throw new RegraNegocioException("CPF_DUPLICADO", "Já existe um cliente cadastrado com este CPF.");
        }
        if (repository.existsByEmail(req.email())) {
            throw new RegraNegocioException("EMAIL_DUPLICADO", "Já existe um cliente cadastrado com este e-mail.");
        }
        if (!req.senha().equals(req.confirmacaoSenha())) {
            throw new RegraNegocioException("RNF0032", "A confirmação de senha não corresponde à senha informada.");
        }
        if (!SenhaValidator.isForte(req.senha())) {
            throw new RegraNegocioException("RNF0031",
                    "Senha fraca: utilize ao menos 8 caracteres, com letra maiúscula, minúscula e caractere especial.");
        }

        String senhaHash = passwordEncoder.encode(req.senha());
        String codigoCliente = gerarCodigoUnico();

        Cliente cliente = new Cliente(
                codigoCliente, req.genero(), req.nome(), req.dataNascimento(),
                cpfLimpo, req.telefoneTipo(), req.telefoneDdd(), req.telefoneNumero(), req.email(), senhaHash,
                req.enderecoTipoResidencia(), req.enderecoTipoLogradouro(), // NOVOS CAMPOS
                req.enderecoLogradouro(), req.enderecoNumero(), req.enderecoBairro(),
                req.enderecoCep(), req.enderecoCidade(), req.enderecoEstado(),
                req.pais() // NOVO CAMPO
        );
        cliente = repository.save(cliente);
        auditoria.registrar("Cliente", cliente.getId(), "INSERT", cliente.getCodigoCliente(),
                "Cadastro: nome=" + cliente.getNome() + ", cpf=" + cliente.getCpf());

        // O endereço informado no cadastro também é replicado como o primeiro
        // registro da lista de endereços (RF0026), do tipo AMBOS: assim ele
        // aparece na tela de "Endereços do cliente" e já satisfaz de imediato
        // as RN0021/RN0022 (ao menos um endereço de cobrança e um de entrega).
        Endereco enderecoResidencial = new Endereco(
                cliente.getId(),
                "Residencial",
                TipoEndereco.AMBOS,
                cliente.getEnderecoTipoResidencia(),
                cliente.getEnderecoTipoLogradouro(),
                cliente.getEnderecoLogradouro(),
                cliente.getEnderecoNumero(),
                cliente.getEnderecoBairro(),
                cliente.getEnderecoCep(),
                cliente.getEnderecoCidade(),
                cliente.getEnderecoEstado(),
                cliente.getPais(),
                "Endereço informado no cadastro do cliente."
        );
        enderecoRepository.save(enderecoResidencial);

        return cliente;
    }

    /** RF0024 — consulta com filtros combinados ou isolados por nome, CPF, e-mail, código do cliente e status. */
    @Transactional(readOnly = true)
    public List<Cliente> consultar(String nome, String cpf, String email, String codigoCliente, StatusCliente status) {
        String cpfLimpo = (cpf == null || cpf.isBlank()) ? null : CpfValidator.somenteDigitos(cpf);
        String nomeFiltro = (nome == null || nome.isBlank()) ? null : nome;
        String emailFiltro = (email == null || email.isBlank()) ? null : email;
        String codigoFiltro = (codigoCliente == null || codigoCliente.isBlank()) ? null : codigoCliente;
        return repository.buscarComFiltros(nomeFiltro, cpfLimpo, emailFiltro, codigoFiltro, status);
    }

    @Transactional(readOnly = true)
    public Cliente buscarPorId(Long id) {
        return repository.findById(id).orElseThrow(() -> new ClienteNaoEncontradoException(id));
    }

    /** RF0022 — altera os dados cadastrais do cliente (exceto CPF e senha). */
    /** RF0022 — altera os dados cadastrais do cliente (exceto CPF e senha). */
    @Transactional
    public Cliente alterar(Long id, AlterarClienteRequest req) {
        Cliente cliente = buscarPorId(id);

        boolean emailMudou = !cliente.getEmail().equalsIgnoreCase(req.email());
        if (emailMudou && repository.existsByEmail(req.email())) {
            throw new RegraNegocioException("EMAIL_DUPLICADO", "Já existe um cliente cadastrado com este e-mail.");
        }

        // Aqui passamos os novos campos da RN0023 exigidos pelo domínio Cliente
        cliente.atualizarDados(
                req.nome(),
                req.telefoneTipo(),
                req.telefoneDdd(),
                req.telefoneNumero(),
                req.email(),
                req.enderecoTipoResidencia(),  // NOVO CAMPO
                req.enderecoTipoLogradouro(),  // NOVO CAMPO
                req.enderecoLogradouro(),
                req.enderecoNumero(),
                req.enderecoBairro(),
                req.enderecoCep(),
                req.enderecoCidade(),
                req.enderecoEstado(),
                req.pais()                     // NOVO CAMPO
        );

        cliente = repository.save(cliente);

        auditoria.registrar("Cliente", cliente.getId(), "UPDATE", cliente.getCodigoCliente(),
                "Alteração de dados cadastrais: nome=" + cliente.getNome() + ", email=" + cliente.getEmail());

        return cliente;
    }

    /**
     * RF0028 — altera apenas a senha, isoladamente, sem exigir os demais dados
     * cadastrais.
     */
    @Transactional
    public Cliente alterarSenha(Long id, AlterarSenhaRequest req) {
        Cliente cliente = buscarPorId(id);

        if (!passwordEncoder.matches(req.senhaAtual(), cliente.getSenhaHash())) {
            throw new RegraNegocioException("SENHA_ATUAL_INCORRETA", "Senha atual incorreta.");
        }
        if (!req.novaSenha().equals(req.confirmacaoNovaSenha())) {
            throw new RegraNegocioException("RNF0032", "A confirmação da nova senha não corresponde à nova senha informada.");
        }
        if (!SenhaValidator.isForte(req.novaSenha())) {
            throw new RegraNegocioException("RNF0031",
                    "Senha fraca: utilize ao menos 8 caracteres, com letra maiúscula, minúscula e caractere especial.");
        }

        cliente.atualizarSenhaHash(passwordEncoder.encode(req.novaSenha()));
        cliente = repository.save(cliente);
        auditoria.registrar("Cliente", cliente.getId(), "UPDATE", cliente.getCodigoCliente(),
                "Senha alterada (valor não registrado por segurança)");
        return cliente;
    }

    /**
     * RF0023 — inativa o cadastro do cliente.
     * IMPORTANTE: esta operação NUNCA remove o registro do banco de dados
     * (ver StatusCliente). Um cliente já inativo não pode ser inativado
     * novamente — essa validação evita marcar novamente a data de inativação
     * e sinaliza ao usuário que a operação já foi realizada.
     */
    @Transactional
    public Cliente inativar(Long id) {
        Cliente cliente = buscarPorId(id);
        if (!cliente.isAtivo()) {
            throw new RegraNegocioException("CLIENTE_JA_INATIVO", "Este cliente já está inativo.");
        }
        cliente.inativar();
        cliente = repository.save(cliente);
        auditoria.registrar("Cliente", cliente.getId(), "UPDATE", cliente.getCodigoCliente(),
                "Status alterado para INATIVO");
        return cliente;
    }

    /** Reativa um cliente inativo (demonstra que a inativação é reversível, isto é, não é uma exclusão). */
    @Transactional
    public Cliente reativar(Long id) {
        Cliente cliente = buscarPorId(id);
        if (cliente.isAtivo()) {
            throw new RegraNegocioException("CLIENTE_JA_ATIVO", "Este cliente já está ativo.");
        }
        cliente.reativar();
        cliente = repository.save(cliente);
        auditoria.registrar("Cliente", cliente.getId(), "UPDATE", cliente.getCodigoCliente(),
                "Status alterado para ATIVO (reativação)");
        return cliente;
    }

    private String gerarCodigoUnico() {
        return "CLI-" + Year.now() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
