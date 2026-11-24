package com.games.recomendacao.application.service;

import com.games.cliente.adapter.out.persistence.repository.ClienteRepository;
import com.games.cliente.application.exception.ClienteNaoEncontradoException;
import com.games.cliente.application.exception.RecursoNaoEncontradoException;
import com.games.cliente.application.exception.RegraNegocioException;
import com.games.cliente.domain.entity.Cliente;
import com.games.cliente.domain.enums.StatusCliente;
import com.games.recomendacao.adapter.out.persistence.repository.MensagemAssistenteRepository;
import com.games.recomendacao.application.gateway.IaGenerativa;
import com.games.recomendacao.application.gateway.IaGenerativa.Turno;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.DadosVendas;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.JogoCatalogo;
import com.games.recomendacao.application.service.PerfilRecomendacaoService.PerfilCliente;
import com.games.recomendacao.domain.entity.MensagemAssistente;
import com.games.recomendacao.domain.entity.TreinamentoModelo;
import com.games.recomendacao.domain.enums.AvaliacaoResposta;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Chatbot de recomendação (RNF0044, itens 1 e 2): ajuda na busca por jogos, responde dúvidas e
 * recomenda de forma personalizada, usando o modelo ajustado mais recente (ou o modelo base, antes do
 * primeiro ajuste fino). Cada resposta fica registrada para o cliente avaliar (item 3).
 */
@Service
public class AssistenteService {

    public static final int TAMANHO_MAXIMO_MENSAGEM = 1000;
    private static final int TROCAS_DE_CONTEXTO = 10;

    private final PerfilRecomendacaoService perfis;
    private final TreinamentoService treinamentos;
    private final IaGenerativa ia;
    private final MensagemAssistenteRepository mensagemRepository;
    private final ClienteRepository clienteRepository;
    private final ConferenciaResposta conferencia;

    public AssistenteService(PerfilRecomendacaoService perfis, TreinamentoService treinamentos, IaGenerativa ia,
                             MensagemAssistenteRepository mensagemRepository, ClienteRepository clienteRepository,
                             ConferenciaResposta conferencia) {
        this.perfis = perfis;
        this.conferencia = conferencia;
        this.treinamentos = treinamentos;
        this.ia = ia;
        this.mensagemRepository = mensagemRepository;
        this.clienteRepository = clienteRepository;
    }

    public MensagemAssistente conversar(Long clienteId, String conversaId, String texto) {
        if (texto == null || texto.isBlank()) {
            throw new RegraNegocioException("RNF0044", "Digite uma mensagem para o assistente.");
        }
        if (texto.length() > TAMANHO_MAXIMO_MENSAGEM) {
            throw new RegraNegocioException("RNF0044", "A mensagem pode ter no máximo " + TAMANHO_MAXIMO_MENSAGEM + " caracteres.");
        }
        var cliente = clienteRepository.findById(clienteId).orElseThrow(() -> new ClienteNaoEncontradoException(clienteId));
        if (cliente.getStatus() != StatusCliente.ATIVO) {
            throw new RegraNegocioException("RNF0044", "O assistente está disponível apenas para clientes ativos.");
        }
        String conversa = conversaId == null || conversaId.isBlank() ? UUID.randomUUID().toString() : conversaId;

        List<MensagemAssistente> anteriores = mensagemRepository.findByClienteIdAndConversaIdOrderByIdAsc(clienteId, conversa);
        List<Turno> turnos = new ArrayList<>();
        for (MensagemAssistente anterior : anteriores.subList(Math.max(0, anteriores.size() - TROCAS_DE_CONTEXTO), anteriores.size())) {
            turnos.add(new Turno(true, anterior.getPergunta()));
            turnos.add(new Turno(false, anterior.getResposta()));
        }
        turnos.add(new Turno(true, texto.trim()));

        PerfilCliente perfil = perfis.perfil(clienteId);
        List<JogoCatalogo> catalogo = perfis.catalogo();
        // pedido por estilo ("quero criar coisas..."), valor ("até R$ 150") ou gênero ("um jogo de corrida"):
        // os jogos que servem para o cliente vão destacados no prompt
        DadosVendas vendas = perfis.dadosVendas();
        String instrucao = perfis.comBlocosDoPedido(perfis.instrucaoSistema(perfil, catalogo, vendas), texto, perfil, catalogo, vendas);
        Optional<TreinamentoModelo> emUso = treinamentos.modeloEmUso();
        String resposta = ia.responder(emUso.map(TreinamentoModelo::getModeloAjustado).orElse(null), instrucao, turnos);
        String modelo = emUso.map(t -> t.getNomeModelo() + " (ajuste fino de " + t.getModeloBase() + ")")
                .orElse(ia.modeloBase() + " (modelo base)");
        // a IA citou um jogo que o cliente já tem, acima do orçamento ou de outra plataforma: vale a resposta conferida
        ConferenciaResposta.Resultado conferida = conferencia.conferir(resposta, texto, perfil, catalogo, vendas);
        if (conferida.substituida()) modelo += " — corrigida pela conferência do perfil";
        return mensagemRepository.save(new MensagemAssistente(clienteId, conversa, texto.trim(), conferida.resposta(), modelo));
    }

    /** As recomendações calculadas para o cliente (o que vai nas SUGESTÕES do prompt), sem passar pela IA. */
    public List<PerfilRecomendacaoService.Recomendacao> recomendacoes(Long clienteId, int quantidade) {
        PerfilCliente perfil = perfis.perfil(clienteId);
        return perfis.recomendar(perfil, perfis.catalogo(), perfis.dadosVendas(), quantidade);
    }

    public MensagemAssistente avaliar(Long mensagemId, AvaliacaoResposta avaliacao) {
        if (avaliacao == null) {
            throw new RegraNegocioException("RNF0044", "Informe se a resposta foi útil ou não útil.");
        }
        MensagemAssistente mensagem = mensagemRepository.findById(mensagemId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mensagem do assistente não encontrada: id=" + mensagemId));
        mensagem.avaliar(avaliacao);
        MensagemAssistente salva = mensagemRepository.save(mensagem);
        treinamentos.verificarRetreinoAutomatico();
        return salva;
    }

    public List<MensagemAssistente> conversa(Long clienteId, String conversaId) {
        return mensagemRepository.findByClienteIdAndConversaIdOrderByIdAsc(clienteId, conversaId);
    }

    public List<MensagemAssistente> avaliacoesDoCliente(Long clienteId) {
        return mensagemRepository.findByClienteIdAndAvaliacaoIsNotNullOrderByDataAvaliacaoDesc(clienteId);
    }

    public List<MensagemAssistente> todasAvaliacoes() {
        return mensagemRepository.findByAvaliacaoIsNotNullOrderByDataAvaliacaoDesc();
    }

    public Map<Long, String> nomesDosClientes(Collection<Long> ids) {
        return clienteRepository.findAllById(ids).stream().collect(Collectors.toMap(Cliente::getId, Cliente::getNome));
    }
}
