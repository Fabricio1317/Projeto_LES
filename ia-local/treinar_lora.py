"""
Ajuste fino local do modelo generativo do assistente da Nexus (RNF0044, item 3).

Disparado pelo backend (TreinamentoService -> OllamaLocal) a cada treino:
  1. lê o dataset JSONL ({"messages": [system, user, assistant, ...]}) montado com as vendas e o feedback;
  2. treina um adaptador LoRA sobre o modelo base (Qwen2.5 1.5B Instruct) na CPU, calculando a perda
     só nas respostas do assistente;
  3. mescla o adaptador no modelo, converte para GGUF (q8_0) com o conversor do llama.cpp e importa
     no Ollama com o nome da versão (ex.: nexus-recomendador-v2), que passa a responder no chat.

O andamento fica em <saida>/status.json, lido pelo backend:
  {"estado": "EM_ANDAMENTO" | "CONCLUIDO" | "FALHOU", "etapa": ..., "progresso": 0..1, ...}
"""
import argparse
import json
import os
import random
import shutil
import subprocess
import sys
import time
import traceback
from pathlib import Path

MODELFILE = '''FROM ./modelo.gguf
TEMPLATE """{{- range .Messages }}<|im_start|>{{ .Role }}
{{ .Content }}<|im_end|>
{{ end }}<|im_start|>assistant
"""
PARAMETER stop "<|im_end|>"
PARAMETER stop "<|endoftext|>"
PARAMETER temperature 0.6
PARAMETER num_ctx 4096
'''


def escrever_status(arquivo: Path, **campos):
    campos["atualizadoEm"] = time.strftime("%Y-%m-%dT%H:%M:%S")
    temporario = arquivo.with_suffix(".tmp")
    temporario.write_text(json.dumps(campos, ensure_ascii=False), encoding="utf-8")
    os.replace(temporario, arquivo)


def tokenizar(tokenizer, mensagens, maximo):
    """Tokens da conversa e rótulos: só as falas do assistente contam na perda."""
    texto = tokenizer.apply_chat_template(mensagens, tokenize=False)
    ids = tokenizer(texto, add_special_tokens=False)["input_ids"]
    rotulos = [-100] * len(ids)
    for i, mensagem in enumerate(mensagens):
        if mensagem["role"] != "assistant":
            continue
        antes = tokenizer.apply_chat_template(mensagens[:i], tokenize=False, add_generation_prompt=True)
        ate = tokenizer.apply_chat_template(mensagens[: i + 1], tokenize=False)
        inicio = len(tokenizer(antes, add_special_tokens=False)["input_ids"])
        fim = len(tokenizer(ate, add_special_tokens=False)["input_ids"])
        rotulos[inicio:fim] = ids[inicio:fim]
    if len(ids) > maximo:
        return None
    return ids, rotulos


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--modelo-base", required=True, help="pasta do modelo base (Hugging Face)")
    parser.add_argument("--saida", required=True)
    parser.add_argument("--nome", required=True, help="nome do modelo no Ollama")
    parser.add_argument("--epocas", type=int, default=2)
    parser.add_argument("--taxa", type=float, default=2e-4)
    parser.add_argument("--rank", type=int, default=16)
    parser.add_argument("--max-tokens", type=int, default=7168)  # catálogo com 82 jogos: exemplos de até ~6.300 tokens
    parser.add_argument("--acumulacao", type=int, default=4)
    parser.add_argument("--camadas", type=int, default=8, help="últimas camadas que recebem o LoRA")
    parser.add_argument("--ollama", default="ollama")
    parser.add_argument("--conversor", required=True, help="llama.cpp/convert_hf_to_gguf.py")
    args = parser.parse_args()

    saida = Path(args.saida)
    saida.mkdir(parents=True, exist_ok=True)
    status = saida / "status.json"
    inicio = time.time()
    escrever_status(status, estado="EM_ANDAMENTO", etapa="Carregando o modelo base", progresso=0.0)

    try:
        import torch
        from peft import LoraConfig, get_peft_model
        from transformers import AutoModelForCausalLM, AutoTokenizer

        torch.manual_seed(42)
        random.seed(42)
        torch.set_num_threads(max(1, (os.cpu_count() or 2) // 2))  # núcleos físicos

        tokenizer = AutoTokenizer.from_pretrained(args.modelo_base)
        conversas = [json.loads(l)["messages"] for l in Path(args.dataset).read_text(encoding="utf-8").splitlines() if l.strip()]
        exemplos = [e for e in (tokenizar(tokenizer, c, args.max_tokens) for c in conversas) if e is not None]
        if not exemplos:
            raise ValueError("Dataset sem exemplos válidos.")

        modelo = AutoModelForCausalLM.from_pretrained(args.modelo_base, dtype=torch.float32)
        # LoRA só nas últimas camadas: as de baixo ficam congeladas e não guardam ativações para o
        # cálculo de volta, o que corta memória e tempo na CPU
        total_camadas = modelo.config.num_hidden_layers
        modelo = get_peft_model(modelo, LoraConfig(
            r=args.rank, lora_alpha=args.rank * 2, lora_dropout=0.05, task_type="CAUSAL_LM",
            target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
            layers_to_transform=list(range(total_camadas - args.camadas, total_camadas))))
        nucleo = modelo.base_model.model  # Qwen2ForCausalLM com os adaptadores

        def calcular_perda(ids, rotulos):
            """Perda só nas posições das respostas: o lm_head (vocabulário de 151 mil) roda só ali."""
            ocultos = nucleo.model(input_ids=torch.tensor([ids]), use_cache=False).last_hidden_state[0]
            alvo = torch.tensor(rotulos[1:])
            posicoes = (alvo != -100).nonzero(as_tuple=True)[0]
            logits = nucleo.lm_head(ocultos[:-1][posicoes])
            return torch.nn.functional.cross_entropy(logits, alvo[posicoes])
        parametros = [p for p in modelo.parameters() if p.requires_grad]
        otimizador = torch.optim.AdamW(parametros, lr=args.taxa, weight_decay=0.0)
        total_passos = max(1, (len(exemplos) * args.epocas) // args.acumulacao)
        agendador = torch.optim.lr_scheduler.LambdaLR(
            otimizador, lambda passo: min(1.0, (passo + 1) / 3) * max(0.05, 1 - passo / total_passos))

        modelo.train()
        total = len(exemplos) * args.epocas
        feitos, perda_media, perdas = 0, None, []
        inicio_treino = time.time()
        for epoca in range(args.epocas):
            random.shuffle(exemplos)
            for ids, rotulos in exemplos:
                perda = calcular_perda(ids, rotulos)
                (perda / args.acumulacao).backward()
                perdas.append(perda.item())
                feitos += 1
                if feitos % args.acumulacao == 0 or feitos == total:
                    torch.nn.utils.clip_grad_norm_(parametros, 1.0)
                    otimizador.step()
                    agendador.step()
                    otimizador.zero_grad()
                perda_media = sum(perdas[-20:]) / len(perdas[-20:])
                decorrido = time.time() - inicio_treino
                escrever_status(status, estado="EM_ANDAMENTO", etapa=f"Treinando (época {epoca + 1} de {args.epocas})",
                                progresso=round(feitos / total, 4), perda=round(perda_media, 4), exemplos=len(exemplos),
                                restanteSegundos=int(decorrido / feitos * (total - feitos)))

        escrever_status(status, estado="EM_ANDAMENTO", etapa="Mesclando o adaptador LoRA no modelo", progresso=1.0,
                        perda=round(perda_media, 4))
        modelo = modelo.merge_and_unload().to(torch.float16)
        modelo.save_pretrained(saida / "modelo", safe_serialization=True)
        tokenizer.save_pretrained(saida / "modelo")
        del modelo

        escrever_status(status, estado="EM_ANDAMENTO", etapa="Convertendo para GGUF", progresso=1.0,
                        perda=round(perda_media, 4))
        conversao = subprocess.run([sys.executable, args.conversor, str(saida / "modelo"), "--outfile",
                                    str(saida / "modelo.gguf"), "--outtype", "q8_0"],
                                   capture_output=True, text=True, encoding="utf-8", errors="replace")
        if conversao.returncode != 0:
            raise RuntimeError("conversão para GGUF falhou: " + (conversao.stderr or conversao.stdout)[-1500:])
        shutil.rmtree(saida / "modelo", ignore_errors=True)  # o GGUF basta; economiza ~3 GB por versão
        (saida / "Modelfile").write_text(MODELFILE, encoding="utf-8")

        escrever_status(status, estado="EM_ANDAMENTO", etapa="Importando o modelo no Ollama", progresso=1.0,
                        perda=round(perda_media, 4))
        resultado = subprocess.run([args.ollama, "create", args.nome, "-f", "Modelfile"], cwd=saida,
                                   capture_output=True, text=True, encoding="utf-8", errors="replace")
        if resultado.returncode != 0:
            raise RuntimeError("ollama create falhou: " + (resultado.stderr or resultado.stdout)[-1500:])

        escrever_status(status, estado="CONCLUIDO", etapa="Concluído", progresso=1.0, modelo=args.nome,
                        perda=round(perda_media, 4), exemplos=len(exemplos), duracaoSegundos=int(time.time() - inicio))
    except Exception as erro:  # noqa: BLE001 — qualquer falha vira status FALHOU para o backend
        escrever_status(status, estado="FALHOU", etapa="Falhou", erro=f"{type(erro).__name__}: {erro}",
                        detalhe=traceback.format_exc()[-3000:])
        sys.exit(1)


if __name__ == "__main__":
    main()
