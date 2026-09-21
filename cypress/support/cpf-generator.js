// Gera CPFs sintéticos, porém matematicamente válidos (dígito verificador
// correto), para uso exclusivo nos testes automatizados. Cada chamada gera
// um CPF diferente, evitando falso-negativo por "CPF_DUPLICADO" quando a
// suíte roda mais de uma vez sobre o mesmo banco.
function calcularDigito(digitos, pesoInicial) {
  let soma = 0;
  for (let i = 0; i < digitos.length; i++) {
    soma += digitos[i] * (pesoInicial - i);
  }
  const resto = soma % 11;
  return resto < 2 ? 0 : 11 - resto;
}

function gerarCpfValido() {
  const nove = Array.from({ length: 9 }, () => Math.floor(Math.random() * 10));
  const dv1 = calcularDigito(nove, 10);
  const dez = [...nove, dv1];
  const dv2 = calcularDigito(dez, 11);
  return [...nove, dv1, dv2].join("");
}

function formatarCpf(cpf) {
  return cpf.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, "$1.$2.$3-$4");
}

module.exports = { gerarCpfValido, formatarCpf };
