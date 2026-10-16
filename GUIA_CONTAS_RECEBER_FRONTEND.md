# Contas a receber, parcelas e mensalidades - guia do frontend

Backend atualizado em 16/10/2026. Este guia complementa o módulo de relatórios e substitui as regras antigas de pagamento exclusivamente integral das mensalidades. O projeto Angular não foi alterado nesta entrega.

## 1. Funcionalidades e regras

As contas a receber agora seguem o padrão financeiro das contas a pagar onde ele se aplica:

- Vários recebimentos por cobrança, com valor recebido e saldo restante calculados no servidor.
- Pagamento parcial mantém `PENDENTE`; quitação integral marca `PAGO`.
- Sem `valorPago`, paga-se todo o saldo restante, preservando clientes antigos.
- Valores positivos, com até duas casas decimais e limitados ao saldo.
- Idempotência opcional no backend e recomendada para todas as confirmações no frontend.
- Consulta do último recibo e de todos os recebimentos da cobrança.
- Competência mensal independente do vencimento.
- Primeiro vencimento em 30 dias; próximas cobranças por mês do calendário, preservando o dia original.
- Histórico protegido contra exclusão e alteração do valor/aluno após recebimento ou quitação histórica.
- Dashboard e inadimplência usam o saldo restante. O caixa usa o valor efetivamente recebido.

Uma matrícula pendente só é ativada quando a cobrança é integralmente quitada. Um recebimento parcial não ativa a matrícula nem desativa uma matrícula que já estava ativa. Mantém-se a regra anterior de ativação pela quitação de uma cobrança; não foi criada uma regra de bloqueio automático por outras mensalidades vencidas.

As permissões existentes foram preservadas: ALUNO paga e consulta recibos apenas das próprias cobranças; ADMIN administra as cobranças e consulta os relatórios. ADMIN não usa o endpoint de pagamento do aluno para se passar por ele. Aluno arquivado não recebe cobranças recorrentes nem novos recebimentos.

Não existe classificação SALARIO/DESPESA nas mensalidades. Essa classificação pertence às contas a pagar.

## 2. Pontos do Angular a atualizar

Frontend conferido: Angular 21 standalone e PrimeNG 21.

```text
src/app/components/conta-receber/
  models/conta-receber.ts
  service/recebimento-service.ts
  components/pagamento-matricula/pagamento-matricula.ts
  components/pagamento-matricula/pagamento-matricula.html
  components/minhas-matriculas/
  components/contas-receber/
src/app/components/dashboard/
```

Reutilizar o componente `PagamentoMatricula`, compartilhado entre a página de mensalidades e o dashboard. Não duplicar a confirmação de pagamento em uma segunda tela. Manter o interceptor JWT, AuthService e os helpers de erro existentes.

## 3. Endpoints e autorização

| Método e URL | Papel | Finalidade |
|---|---|---|
| `GET /contareceber/minhas` | ALUNO | Próprias cobranças, com posição financeira |
| `POST /recebimentos/realizar/{contaId}` | ALUNO | Registrar pagamento integral ou parcial |
| `GET /recebimentos/recibo/conta/{contaId}` | ALUNO | Recibo mais recente por data/ID |
| `GET /recebimentos/conta/{contaId}` | ALUNO | Todos os recibos, do mais recente para o mais antigo |
| `GET /contareceber/listar` | ADMIN | Todas as cobranças |
| `GET /contareceber/buscar-contareceber/{id}` | ADMIN | Uma cobrança com posição financeira |
| `POST /contareceber/salvar-contareceber` | ADMIN | Criar cobrança ou atualizar quando enviado um ID existente |
| `PUT /contareceber/atualizar-contareceber/{id}` | ADMIN | Atualizar campos permitidos |
| `DELETE /contareceber/deletar-contareceber/{id}` | ADMIN | Excluir cobrança sem recebimentos/quitação histórica |

O ID do aluno autenticado vem do JWT. Não enviar `alunoId` no pagamento, histórico ou recibo. Cobrança de outro aluno retorna 404 também quando a chave de idempotência for conhecida. Sem token, 401; papel incompatível, 403.

Histórico de cobrança existente e sem recebimentos retorna `[]`; recibo mais recente sem recebimento retorna 404. Esse 404 não indica que a cobrança foi excluída.

## 4. Contratos TypeScript

Atualizar `models/conta-receber.ts`, preservando os nomes dos tipos já importados pelo projeto:

```ts
export type StatusFinanceiro = 'PENDENTE' | 'PAGO' | 'ATRASADO';
export type FormaPagamento = 'PIX' | 'DINHEIRO' | 'CARTAO' | 'BOLETO' | 'TRANSFERENCIA';

export interface ContaReceber {
  id: number;
  descricao: string | null;
  valor: number | null; // valor original da cobrança
  dataVencimento: string | null;
  status: StatusFinanceiro | null;
  alunoId: number;
  nomeAluno: string | null;
  competencia: string | null; // yyyy-MM
  valorRecebido: number;
  saldoRestante: number;
  recebimentoParcial: boolean;
  avisos: string[];
}

export interface ReciboMatricula {
  id: number;
  contaId: number;
  valor: number; // somente o valor deste recebimento
  dataRecebimento: string;
  formaPagamento: FormaPagamento | null;
  nomeAluno: string | null;
  descricaoConta: string | null;
}

export interface CobrancaParaPagamento {
  id: number;
  descricao: string | null;
  valor: number | null;
  dataVencimento: string | null;
  status: StatusFinanceiro | null;
  competencia: string | null;
  valorRecebido: number;
  saldoRestante: number;
  recebimentoParcial: boolean;
  avisos: string[];
}

export interface ContaReceberRequest {
  id?: number;
  descricao: string;
  valor: number;
  dataVencimento: string;
  aluno: { id: number };
  competencia?: string;
  status?: StatusFinanceiro; // não usar para quitar ou reabrir manualmente
}
```

O JSON continua enviando `valor` como valor original. O campo a usar no botão e modal de pagamento é **`saldoRestante`**. Não recalcular `valor - valorRecebido` no navegador: divergências legadas podem exigir outro tratamento, já aplicado pelo servidor.

Não acrescentar `PARCIAL` ao enum. Usar `recebimentoParcial` como indicação complementar ao status financeiro.

## 5. Service de recebimentos

Adaptar o service existente, mantendo o nome `pagar`:

```ts
import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { FormaPagamento, ReciboMatricula } from '../models/conta-receber';

@Injectable({ providedIn: 'root' })
export class RecebimentoService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/recebimentos`;

  pagar(contaId: number, formaPagamento: FormaPagamento,
        valorPago?: number, chave?: string): Observable<ReciboMatricula> {
    let params = new HttpParams().set('formaPagamento', formaPagamento);
    if (valorPago !== undefined) params = params.set('valorPago', String(valorPago));
    const headers: Record<string, string> = {};
    if (chave) headers['Idempotency-Key'] = chave;
    return this.http.post<ReciboMatricula>(`${this.apiUrl}/realizar/${contaId}`, null, { params, headers });
  }

  buscarRecibo(contaId: number): Observable<ReciboMatricula> {
    return this.http.get<ReciboMatricula>(`${this.apiUrl}/recibo/conta/${contaId}`);
  }

  listarRecibos(contaId: number): Observable<ReciboMatricula[]> {
    return this.http.get<ReciboMatricula[]>(`${this.apiUrl}/conta/${contaId}`);
  }
}
```

Exemplo de parcela:

```http
POST /recebimentos/realizar/123?formaPagamento=PIX&valorPago=100.00
Authorization: Bearer <token>
Idempotency-Key: 1d830a80-aa48-4a89-8525-807ab108bbde
```

Exemplo compatível com o cliente anterior: `POST /recebimentos/realizar/123?formaPagamento=PIX`. Agora paga o saldo restante, que pode ser menor que o valor original.

## 6. Modal de pagamento parcial

No componente `PagamentoMatricula`, acrescentar um `FormControl<number | null>` para o valor, preenchido com `conta.saldoRestante`, e uma intenção de pagamento persistida para recuperação de falhas.

```ts
readonly valorPago = new FormControl<number | null>(null, {
  validators: [Validators.required, Validators.min(0.01)],
});

interface IntencaoPagamento {
  usuarioId: number;
  contaId: number;
  formaPagamento: FormaPagamento;
  valorPago: number;
  chave: string;
}

// Adicionar como estado da classe, não recriar ao repetir uma tentativa.
// private intencao: IntencaoPagamento | null = null;
```

Atualizar `podePagar` para verificar saldo positivo e finito. Impedir ação para `QUITACAO_SEM_COMPROVANTE` ou `RECEBIMENTO_NEGATIVO_LEGADO`, apresentando a orientação de revisão. A confirmação no backend continua sendo a autoridade final.

```ts
podePagar(conta: CobrancaParaPagamento): boolean {
  return Number.isFinite(conta.saldoRestante) && conta.saldoRestante > 0 &&
    !conta.avisos.includes('QUITACAO_SEM_COMPROVANTE') &&
    !conta.avisos.includes('RECEBIMENTO_NEGATIVO_LEGADO');
}
```

Em `abrirPagamento`, se não há tentativa pendente para a mesma conta, usar:

```ts
this.valorPago.setValidators([
  Validators.required, Validators.min(0.01), Validators.max(conta.saldoRestante),
]);
this.valorPago.setValue(conta.saldoRestante);
this.valorPago.updateValueAndValidity({ emitEvent: false });
```

Trecho de template dentro do diálogo existente:

```html
@if (contaSelecionada(); as conta) {
  <p>Valor original: {{ conta.valor | currency:'BRL' }}</p>
  <p>Já recebido: {{ conta.valorRecebido | currency:'BRL' }}</p>
  <p><strong>Saldo restante: {{ conta.saldoRestante | currency:'BRL' }}</strong></p>

  <label for="valor-parcela">Valor deste pagamento</label>
  <p-inputnumber inputId="valor-parcela" [formControl]="valorPago"
    mode="currency" currency="BRL" locale="pt-BR"
    [min]="0.01" [max]="conta.saldoRestante"
    [minFractionDigits]="2" [maxFractionDigits]="2" />

  <small>Um pagamento parcial deixa o restante em aberto. A matrícula pendente será ativada após a quitação integral.</small>
}
```

Não usar campo livre com string monetária `100,00` no HTTP. O InputNumber trabalha com número; o service envia decimal com ponto.

Na confirmação:

1. Validar valor/forma, saldo e ausência de requisição em andamento.
2. Criar a intenção e a chave com `crypto.randomUUID()` somente quando ainda não houver uma tentativa correspondente.
3. Salvar a intenção em `sessionStorage`, vinculada ao ID do usuário e da conta, antes da chamada HTTP.
4. Desabilitar valor, forma, confirmação e fechamento durante a requisição.
5. Chamar `pagar(conta.id, intencao.formaPagamento, intencao.valorPago, intencao.chave)`.
6. Em sucesso, remover a intenção persistida, mostrar “Pagamento registrado”, emitir `concluido` e recarregar os dados.
7. Reconsultar a lista/dashboard antes de permitir outro pagamento; o recibo retornado contém só aquela parcela.

O componente já possui `pagando`, `erroPagamento`, `concluido` e `reconsultar`: reutilizá-los. A mensagem antiga “A cobrança foi paga” deve ser substituída por uma mensagem que não declare quitação após uma parcela.

## 7. Falha de rede e idempotência

Uma falha de rede pode ocorrer depois de o servidor gravar o recebimento. Nessa situação, **não gerar outra chave e não recriar o valor pela nova consulta de saldo**.

Oferecer “Repetir confirmação”, enviando a mesma conta, forma, valor e chave da intenção persistida. O servidor retorna o recebimento original. Para tentativas que omitiram `valorPago`, manter a omissão; não substituir pelo saldo atualizado.

Se a mesma chave for reutilizada com parâmetros diferentes, a API retorna 409. A confirmação já quitada também pode ser recuperada pela mesma chave. Após arquivamento ou revogação do acesso, as regras de acesso continuam valendo e podem impedir a repetição; encaminhar a conferência à administração.

Ao restaurar uma intenção de `sessionStorage`, conferir o usuário autenticado e a conta antes de usá-la. Não reutilizar intenções de outro login. Em resposta incerta/500, manter a intenção para repetição. Erros conclusivos de validação permitem corrigir o formulário e gerar uma nova intenção; 409 exige primeiro reconsulta e leitura da mensagem.

O componente atual bloqueia qualquer nova tentativa após `falhaRede`. Adaptar esse estado para bloquear uma **nova intenção** e permitir a repetição da intenção original.

## 8. Histórico e recibos

O botão de histórico deve estar disponível quando `valorRecebido > 0`, mesmo com status `PENDENTE`. A condição antiga `status === 'PAGO'` impediria consultar comprovantes de parcelas.

Usar `listarRecibos(conta.id)` para abrir uma tabela com data, forma, valor e ID. A ordem do backend é decrescente por data e ID. Cada item já possui os dados necessários para o diálogo de recibo existente.

```html
<p-table [value]="recibos()" [stripedRows]="true">
  <ng-template #header>
    <tr><th>Data</th><th>Forma</th><th>Valor recebido</th><th>Recibo</th></tr>
  </ng-template>
  <ng-template #body let-r>
    <tr>
      <td>{{ r.dataRecebimento | date:'dd/MM/yyyy':'UTC' }}</td>
      <td>{{ r.formaPagamento ?? 'Não informada' }}</td>
      <td>{{ r.valor | currency:'BRL' }}</td>
      <td><p-button label="Visualizar" (onClick)="abrirReciboExistente(r)" /></td>
    </tr>
  </ng-template>
  <ng-template #emptymessage><tr><td colspan="4">Nenhum recebimento registrado.</td></tr></ng-template>
</p-table>
```

Nesse trecho, declarar `readonly recibos = signal<ReciboMatricula[]>([])` e implementar `abrirReciboExistente(r)` definindo o signal `recibo` já existente. A chamada do histórico deve usar `takeUntilDestroyed`, tratamento de erro e estado de carregamento como os demais métodos do componente.

`buscarRecibo` permanece para acesso rápido ao mais recente. Um recibo de R$ 100 de uma cobrança de R$ 300 não representa quitação integral; evitar o título “Comprovante de quitação” para todo recebimento.

## 9. Lista de mensalidades e dashboard

Mostrar por cobrança:

- Competência, descrição e vencimento.
- Valor original, valor recebido e saldo restante.
- Status financeiro e indicação “Parcial” quando `recebimentoParcial=true`.
- Botões de pagamento e histórico conforme as regras acima.
- Avisos de regularização.

O objeto `financeiro.cobrancaPrincipal` do dashboard recebeu os mesmos campos financeiros adicionais: `competencia`, `valorRecebido`, `saldoRestante`, `recebimentoParcial` e `avisos`. Preserva `id`, `descricao`, `valor`, `dataVencimento`, `status`, `situacao` e `diasParaVencimento`.

Atualizar o adapter para `CobrancaParaPagamento` para repassar esses campos. Os totais `financeiro.emAberto.valor` e `financeiro.vencidas.valor` agora somam saldos restantes. Não somar o valor nominal no frontend.

Após `concluido`, recarregar as cobranças, o dashboard e o perfil autenticado pelos mecanismos existentes. Não definir `statusMatricula='ATIV0'` localmente com base apenas no sucesso do POST: pode ter sido uma parcela. Os enums `ATIV0`/`INATIV0` continuam usando o algarismo zero.

No relatório de inadimplência, cada cobrança preserva `valor` original e passa a incluir:

```ts
competencia: string | null;
valorRecebido: number;
saldoRestante: number;
recebimentoParcial: boolean;
avisos: string[];
```

A coluna “Em aberto” deve exibir `saldoRestante`. `total` por aluno e `totalGeral` são os saldos já calculados. O PDF JasperReports também foi atualizado para mostrar original/recebido e o restante em aberto. O fluxo continua listando cada recebimento na data efetiva.

## 10. Administração, competência e recorrência

Exemplo de criação manual por ADMIN:

```json
{
  "descricao":"Mensalidade de outubro",
  "valor":300,
  "dataVencimento":"2026-11-05",
  "aluno":{"id":42},
  "competencia":"2026-10"
}
```

O valor deve ser positivo, com até duas casas decimais; aluno e vencimento são obrigatórios no contrato administrativo. Competência é `yyyy-MM`, nunca `yyyy-MM-01`. Omitida, usa o mês do vencimento para compatibilidade; o formulário novo deve enviá-la explicitamente.

Novas cobranças duplicadas por aluno/competência são rejeitadas. Cobranças legadas duplicadas não foram apagadas; a administração pode corrigir sua competência, respeitando as validações. Competência/descrição/vencimento podem ser corrigidos; valor e aluno ficam protegidos após recebimentos ou quitação histórica. Status não pode ser usado para efetuar/desfazer pagamentos.

Na edição, enviar os dados atuais completos, preservando o valor e o aluno quando protegidos. Não enviar saldo ou valor recebido como campos editáveis. Alterar manualmente o vencimento redefine o dia usado para a recorrência daquela sequência.

Exemplo de calendário: vencimento em 31/01/2027 gera 28/02/2027, depois 31/03/2027; em ano bissexto, fevereiro usa dia 29. A competência avança um mês separadamente do vencimento. A primeira cobrança automática continua vencendo 30 dias após o cadastro. A rotina gera uma próxima cobrança por execução quando a última vence e não inicia uma nova sequência após restauração de aluno arquivado.

## 11. Dados antigos e erros

A V10 preserva contas e recibos existentes, remove a exclusividade de um recebimento por cobrança e preenche competência/dia original a partir do vencimento. Reabre contas cujo total recebido comprovado seja insuficiente. Não altera automaticamente `statusMatricula` dos alunos durante a migração.

Quitações antigas sem comprovante continuam quitadas com aviso e não criam entrada fictícia no caixa. Não criar um novo recebimento no frontend para “regularizar” automaticamente esse aviso.

| Código de aviso | Apresentação |
|---|---|
| `VALOR_INVALIDO` | Regularizar o valor da cobrança |
| `COMPETENCIA_AUSENTE` | Informar competência |
| `VENCIMENTO_AUSENTE` | Informar vencimento |
| `RECEBIMENTO_SUPERIOR_A_CONTA` | Recebimentos antigos superiores ao valor |
| `RECEBIMENTO_NEGATIVO_LEGADO` | Recebimento antigo negativo exige revisão |
| `QUITACAO_SEM_COMPROVANTE` | Quitação histórica sem comprovante registrado |
| `STATUS_DIVERGENTE` | Status diverge dos recebimentos registrados |

Erros seguem `application/problem+json` com `status`, `title` e `detail`; validação do CRUD pode incluir `errors`. Reutilizar `mensagemApi`.

- 400: valor/chave/competência/formulário inválido ou tentativa de editar status.
- 401/403: fluxo de autenticação e acesso já existente.
- 404: cobrança inexistente/alheia; no endpoint de recibo, também ausência de recibo.
- 409: conta quitada, aluno arquivado, alteração protegida, competência duplicada ou chave reutilizada com outros parâmetros. Ler `detail`; não tratar todo 409 como “já pago”.

Datas de negócio são strings `yyyy-MM-dd`; formatar com `DatePipe` e `UTC` para não retroceder um dia. Valores são calculados em BigDecimal no backend. Renderizar descrições por interpolação, sem `[innerHTML]`.

## 12. Exemplos de resultado

Cobrança de R$ 300 após uma parcela de R$ 100:

```json
{
  "id":123,"descricao":"Mensalidade de outubro","valor":300,"dataVencimento":"2026-11-05",
  "status":"PENDENTE","alunoId":42,"nomeAluno":"João da Conceição","competencia":"2026-10",
  "valorRecebido":100,"saldoRestante":200,"recebimentoParcial":true,"avisos":[]
}
```

Recibo retornado pelo POST e pelo histórico:

```json
{
  "id":51,"contaId":123,"valor":100,"dataRecebimento":"2026-10-16",
  "formaPagamento":"PIX","nomeAluno":"João da Conceição","descricaoConta":"Mensalidade de outubro"
}
```

Os IDs e dados são fictícios; as propriedades correspondem aos contratos do backend.

## 13. Checklist de integração

- [ ] Models e adapter do dashboard incluem os novos campos.
- [ ] Modal usa saldo restante, aceita parcela e valida duas casas decimais.
- [ ] Uma parcela não é exibida como quitação integral.
- [ ] Matrícula só é atualizada a partir da reconsulta ao servidor.
- [ ] Intenção/chave preservadas em falha de rede e separadas por usuário/conta.
- [ ] Repetição usa os mesmos parâmetros; duplo clique não cria duas intenções.
- [ ] Histórico disponível para cobranças parcialmente pagas.
- [ ] Lista de recibos não confunde o último recebimento com o total recebido.
- [ ] Administração usa competência mensal e respeita os campos protegidos.
- [ ] Inadimplência e dashboard mostram saldo restante.
- [ ] Datas, estados vazios, avisos e erros 400/401/403/404/409 testados.
- [ ] Logout/login não reutiliza intenção de outro usuário.
- [ ] Build e testes Angular executados após integrar as alterações.

## 14. Backend e homologação

Reiniciar o backend atualizado com JDK 21 para aplicar a V10 via Flyway. Não executar a migration pelo navegador nem registrar recebimentos artificiais durante a atualização da interface.

Testes PostgreSQL novos usam `RECEBER_TEST_DB_URL`, `RECEBER_TEST_DB_USER` e `RECEBER_TEST_DB_PASSWORD`; criam e removem apenas schemas temporários `test_receber_*`. A validação inclui múltiplas parcelas, concorrência, idempotência, propriedade, ativação, calendário e migração V9→V10. O PDF de QA com dados fictícios fica em `target/contas-receber-exemplos/inadimplencia-parcial.pdf`.
