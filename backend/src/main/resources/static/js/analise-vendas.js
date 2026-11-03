// Análise de vendas por categoria (RF0055 / RNF0043), restrita ao perfil administrador:
// período (início e fim), categorias a comparar e gráfico de linhas com o valor vendido por mês.

const MAXIMO_CATEGORIAS = 8;
const PERFIL_CHAVE = "nexus.perfil";

const analise = {
  slotPorCategoria: new Map(), // a cor acompanha a categoria enquanto ela estiver selecionada
  ultimoResultado: null,
};

// --------------------- perfil (sem login: escolhido na barra superior) ---------------------
function aplicarPerfil() {
  const admin = perfilAtual() === "ADMINISTRADOR";
  document.querySelector("[data-cy=nav-analise]").hidden = !admin;
  if (!admin && !el("tab-analise").hidden) document.querySelector('[data-tab="tab-cadastro"]').click();
}

el("perfil-usuario").addEventListener("change", (ev) => {
  try { sessionStorage.setItem(PERFIL_CHAVE, ev.target.value); } catch { /* sem armazenamento: só não lembra */ }
  aplicarPerfil();
});

try {
  const salvo = sessionStorage.getItem(PERFIL_CHAVE);
  if (salvo) el("perfil-usuario").value = salvo;
} catch { /* segue com o perfil padrão */ }
aplicarPerfil();

// --------------------- filtros ---------------------
document.querySelector('[data-tab="tab-analise"]').addEventListener("click", carregarAnalise);

function dataIso(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

async function carregarAnalise() {
  limparMensagem();
  const admin = perfilAtual() === "ADMINISTRADOR";
  el("analise-acesso-negado").hidden = admin;
  el("form-analise").hidden = !admin;
  if (!admin) {
    el("analise-resultado").hidden = true;
    return;
  }
  const form = el("form-analise");
  if (!form.elements["inicio"].value) {
    const hoje = new Date();
    form.elements["inicio"].value = dataIso(new Date(hoje.getFullYear(), hoje.getMonth() - 6, 1));
    form.elements["fim"].value = dataIso(hoje);
  }
  try {
    renderizarCategorias(await AnaliseApi.categorias());
  } catch (erro) {
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
}

function renderizarCategorias(categorias) {
  el("lista-categorias").innerHTML = categorias.map((c) => {
    const slot = analise.slotPorCategoria.get(c);
    return `
      <label class="chip-categoria">
        <input type="checkbox" data-cy="categoria-check" value="${c}" ${slot ? "checked" : ""}>
        <span class="amostra-cor" ${slot ? `style="background: var(--serie-${slot})"` : ""}></span>${c}
      </label>`;
  }).join("");
}

el("lista-categorias").addEventListener("change", (ev) => {
  const caixa = ev.target.closest("[data-cy=categoria-check]");
  if (!caixa) return;
  const categoria = caixa.value;
  if (caixa.checked) {
    const usados = new Set(analise.slotPorCategoria.values());
    if (usados.size >= MAXIMO_CATEGORIAS) {
      caixa.checked = false;
      mostrarMensagem(`Selecione no máximo ${MAXIMO_CATEGORIAS} categorias para comparar.`, "aviso");
      return;
    }
    let slot = 1;
    while (usados.has(slot)) slot++;
    analise.slotPorCategoria.set(categoria, slot);
  } else {
    analise.slotPorCategoria.delete(categoria);
  }
  const amostra = caixa.nextElementSibling;
  amostra.style.background = caixa.checked ? `var(--serie-${analise.slotPorCategoria.get(categoria)})` : "";
});

el("form-analise").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  limparMensagem();
  const form = ev.target;
  const categorias = [...document.querySelectorAll("[data-cy=categoria-check]:checked")].map((c) => c.value);
  try {
    const inicioConsulta = performance.now();
    const historico = await AnaliseApi.vendasPorCategoria(form.elements["inicio"].value, form.elements["fim"].value, categorias);
    const ms = Math.round(performance.now() - inicioConsulta);
    el("analise-tempo").textContent = `Consulta respondida em ${ms} ms`;
    el("analise-tempo").dataset.ms = ms;
    analise.ultimoResultado = historico;
    renderizarResultado(historico);
  } catch (erro) {
    el("analise-resultado").hidden = true;
    mostrarMensagem(mensagemDeErro(erro), "erro");
  }
});

// --------------------- resultado ---------------------
const corDaSerie = (categoria) => `var(--serie-${analise.slotPorCategoria.get(categoria) || 1})`;
const formatarEixo = (v) => "R$ " + v.toLocaleString("pt-BR", { maximumFractionDigits: 0 });

function renderizarResultado(h) {
  el("analise-resultado").hidden = false;
  el("analise-vazio").hidden = h.series.some((s) => s.total > 0);
  el("analise-legenda").innerHTML = h.series.map((s) => `
    <li data-cy="legenda-item" data-categoria="${s.categoria}">
      <span class="amostra-cor" style="background: ${corDaSerie(s.categoria)}"></span>
      ${s.categoria} <strong data-cy="legenda-total">${formatarMoeda(s.total)}</strong>
    </li>`).join("");
  desenharGrafico(h);
  renderizarTabelaAnalise(h);
}

function escalaMaxima(valor) {
  if (valor <= 0) return 100;
  const potencia = 10 ** Math.floor(Math.log10(valor));
  const n = valor / potencia;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * potencia;
}

// RNF0043 — gráfico de linhas: eixo X com os meses (mês/ano) e eixo Y com o valor vendido em reais
function desenharGrafico(h) {
  const area = el("analise-grafico");
  const largura = Math.max(area.clientWidth, 320);
  const altura = 320;
  const rotulosDiretos = h.series.length <= 4;
  const m = { topo: 16, direita: rotulosDiretos ? 120 : 20, base: 40, esquerda: 84 };
  const larguraUtil = largura - m.esquerda - m.direita;
  const alturaUtil = altura - m.topo - m.base;

  const maximo = escalaMaxima(Math.max(0, ...h.series.flatMap((s) => s.valores)));
  const passoX = h.meses.length > 1 ? larguraUtil / (h.meses.length - 1) : 0;
  const x = (i) => m.esquerda + (h.meses.length > 1 ? i * passoX : larguraUtil / 2);
  const y = (v) => m.topo + alturaUtil - (v / maximo) * alturaUtil;

  const grade = [0, 1, 2, 3, 4].map((i) => {
    const valor = (maximo / 4) * i;
    return `<line class="grade" x1="${m.esquerda}" x2="${largura - m.direita}" y1="${y(valor)}" y2="${y(valor)}"></line>
      <text class="eixo-y" data-cy="eixo-y-rotulo" x="${m.esquerda - 10}" y="${y(valor) + 4}">${formatarEixo(valor)}</text>`;
  }).join("");

  // com muitos meses, mostra um rótulo a cada N para não encavalar
  const intervalo = Math.max(1, Math.ceil(h.meses.length / Math.max(1, Math.floor(larguraUtil / 70))));
  const eixoX = h.meses.map((mes, i) => `
    <text class="eixo-x" data-cy="eixo-x-rotulo" data-mes="${mes.chave}" x="${x(i)}" y="${altura - m.base + 22}"
          ${i % intervalo === 0 ? "" : 'visibility="hidden"'}>${mes.rotulo}</text>`).join("");

  const linhas = h.series.map((s) => {
    const pontos = s.valores.map((v, i) => `${x(i)},${y(v)}`).join(" ");
    const marcadores = s.valores.map((v, i) => `
      <circle class="ponto" data-cy="ponto" data-categoria="${s.categoria}" data-mes="${h.meses[i].chave}" data-valor="${v}"
              cx="${x(i)}" cy="${y(v)}" r="4" style="fill: ${corDaSerie(s.categoria)}"></circle>`).join("");
    return `<g data-cy="linha-categoria" data-categoria="${s.categoria}">
      <polyline class="linha" points="${pontos}" style="stroke: ${corDaSerie(s.categoria)}"></polyline>${marcadores}</g>`;
  }).join("");

  let rotulos = "";
  if (rotulosDiretos) {
    const fim = h.series.map((s) => ({ categoria: s.categoria, y: y(s.valores[s.valores.length - 1]) }))
      .sort((a, b) => a.y - b.y);
    fim.forEach((r, i) => { if (i > 0 && r.y - fim[i - 1].y < 14) r.y = fim[i - 1].y + 14; });
    // não deixa os rótulos descerem para a área do eixo X: empurra o grupo para cima a partir da base
    for (let i = fim.length - 1; i >= 0; i--) {
      fim[i].y = Math.min(fim[i].y, i === fim.length - 1 ? y(0) : fim[i + 1].y - 14);
    }
    rotulos = fim.map((r) => `<text class="rotulo-direto" x="${x(h.meses.length - 1) + 10}" y="${r.y + 4}">${r.categoria}</text>`).join("");
  }

  area.innerHTML = `
    <svg width="${largura}" height="${altura}" role="img" data-cy="grafico-linhas"
         aria-label="Gráfico de linhas do valor vendido por mês para ${h.series.map((s) => s.categoria).join(", ")}">
      ${grade}${eixoX}
      <line class="eixo" x1="${m.esquerda}" x2="${largura - m.direita}" y1="${y(0)}" y2="${y(0)}"></line>
      <line class="mira" x1="0" x2="0" y1="${m.topo}" y2="${m.topo + alturaUtil}" visibility="hidden"></line>
      ${linhas}${rotulos}
      <rect class="captura" x="${m.esquerda - 10}" y="${m.topo}" width="${larguraUtil + 20}" height="${alturaUtil}"></rect>
    </svg>
    <div class="tooltip-grafico" data-cy="tooltip-grafico" hidden></div>`;

  // hover: mira vertical no mês mais próximo e tooltip com o valor de cada categoria
  const svg = area.querySelector("svg");
  const mira = svg.querySelector(".mira");
  const tooltip = area.querySelector(".tooltip-grafico");
  svg.querySelector(".captura").addEventListener("mousemove", (ev) => {
    const px = ev.clientX - svg.getBoundingClientRect().left;
    const i = h.meses.length > 1 ? Math.min(h.meses.length - 1, Math.max(0, Math.round((px - m.esquerda) / passoX))) : 0;
    mira.setAttribute("x1", x(i));
    mira.setAttribute("x2", x(i));
    mira.setAttribute("visibility", "visible");
    tooltip.innerHTML = `<strong>${h.meses[i].rotulo}</strong>` + h.series.map((s) => `
      <div><span class="amostra-cor" style="background: ${corDaSerie(s.categoria)}"></span>${s.categoria}
      <span class="valor">${formatarMoeda(s.valores[i])}</span></div>`).join("");
    tooltip.hidden = false;
    const esquerda = x(i) + 14 + tooltip.offsetWidth > largura ? x(i) - 14 - tooltip.offsetWidth : x(i) + 14;
    tooltip.style.left = `${esquerda}px`;
    tooltip.style.top = `${m.topo}px`;
  });
  svg.querySelector(".captura").addEventListener("mouseleave", () => {
    mira.setAttribute("visibility", "hidden");
    tooltip.hidden = true;
  });
}

// tabela com os mesmos valores do gráfico (leitura sem depender da cor)
function renderizarTabelaAnalise(h) {
  el("thead-analise").innerHTML = `<tr><th>Mês</th>${h.series.map((s) => `<th>${s.categoria}</th>`).join("")}</tr>`;
  const linhas = h.meses.map((mes, i) => `
    <tr data-cy="linha-analise" data-mes="${mes.chave}">
      <td>${mes.rotulo}</td>
      ${h.series.map((s) => `<td class="preco" data-cy="valor-celula" data-categoria="${s.categoria}" data-mes="${mes.chave}"
            data-valor="${s.valores[i]}">${formatarMoeda(s.valores[i])}</td>`).join("")}
    </tr>`).join("");
  const total = `<tr class="linha-total"><td>Total</td>${h.series.map((s) => `<td class="preco">${formatarMoeda(s.total)}</td>`).join("")}</tr>`;
  el("tbody-analise").innerHTML = linhas + total;
}

let redimensionando = null;
window.addEventListener("resize", () => {
  clearTimeout(redimensionando);
  redimensionando = setTimeout(() => {
    if (analise.ultimoResultado && !el("analise-resultado").hidden) desenharGrafico(analise.ultimoResultado);
  }, 150);
});
