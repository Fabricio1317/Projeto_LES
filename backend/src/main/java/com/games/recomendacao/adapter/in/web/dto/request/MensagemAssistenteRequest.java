package com.games.recomendacao.adapter.in.web.dto.request;

/** Mensagem do cliente para o assistente; sem conversaId, começa uma conversa nova. */
public record MensagemAssistenteRequest(String conversaId, String mensagem) {
}
