# Relatórios financeiros do BusSystem - guia do frontend

Entrega do backend: 16/10/2026. Este guia descreve a integração no Angular 21 standalone com PrimeNG 21. O código Angular não foi alterado nesta entrega.

## 1. O que já está no backend

O backend consulta os dados, calcula os totais e gera os PDFs usando **JasperReports 7.0.8**. O navegador apresenta as consultas JSON e baixa o PDF pronto. Não instalar JasperReports no frontend nem recriar os relatórios com jsPDF.

| Recurso | Consulta JSON | Download PDF |
|---|---|---|
| Inadimplência | `GET /relatorios/inadimplencia` | `GET /relatorios/inadimplencia/pdf` |
| Fluxo de caixa | `GET /relatorios/fluxo-caixa` | `GET /relatorios/fluxo-caixa/pdf` |
| Acerto de motoristas | `GET /relatorios/acerto-motoristas` | `GET /relatorios/acerto-motoristas/pdf` |

Todos exigem JWT de **ADMIN**. ALUNO e MOTORISTA recebem 403; sem autenticação, 401. As mesmas restrições são aplicadas nos serviços Java.

Cada rota PDF recebe exatamente os mesmos parâmetros de sua consulta JSON. As consultas não geram contas, pagamentos ou movimentações. Tela e PDF consultados em momentos diferentes podem refletir pagamentos feitos entre as requisições; mostrar a data de emissão retornada, sem prometer um snapshot compartilhado entre requisições.

Os PDFs usam BusSystem, verde esmeralda do tema Aura, fonte DejaVu Sans incorporada, tabelas alternadas e paginação. Inadimplência e acerto são A4 retrato; fluxo é A4 paisagem.

## 2. Estrutura e navegação

Referências conferidas no frontend existente:

- `src/app/app.routes.ts`;
- `src/app/core/auth/guards/auth.guards.ts`, funções `authGuard` e `roleGuard`;
- `src/app/core/auth/interceptors/auth.interceptor.ts`;
- `src/app/core/auth/utils/api-error.ts`, função `mensagemApi`;
- `src/app/components/sidebar/components/sidebar/sidebar.ts`;
- `src/app/components/conta-pagar/service/pagamento-service.ts`;
- `src/app/components/conta-pagar/components/conta-pagar/conta-pagar.ts`.

Estrutura proposta:

```text
src/app/components/relatorios/
  models/relatorios.models.ts
  service/relatorios-service.ts
  components/relatorios/relatorios.ts
  components/relatorios/relatorios.html
  components/relatorios/relatorios.css
  components/saldo-base/saldo-base.ts
```

Adicionar a rota ao array existente, antes de qualquer curinga:

```ts
{
  path: 'relatorios',
  loadComponent: () => import('./components/relatorios/components/relatorios/relatorios')
    .then(m => m.Relatorios),
  canActivate: [authGuard, roleGuard(['ADMIN'])],
},
```

Na seção Financeiro do menu exclusivo de ADMIN, acrescentar:

```ts
{ label: 'Relatórios', icon: 'pi pi-file-pdf', rota: '/relatorios' },
```

Reutilizar o interceptor JWT, `environment.apiUrl`, o tema Aura e as funções de erro. Não acrescentar armazenamento ou interceptor de autenticação.

## 3. Filtros e regras de apresentação

| Relatório | Parâmetro | Obrigatório | Formato |
|---|---|---|---|
| Inadimplência | `alunoId` | Não | ID de aluno existente |
| Inadimplência | `vencimentoInicio` | Não | `yyyy-MM-dd` |
| Inadimplência | `vencimentoFim` | Não | `yyyy-MM-dd` |
| Fluxo | `dataInicio` | Sim | `yyyy-MM-dd` |
| Fluxo | `dataFim` | Sim | `yyyy-MM-dd` |
| Acerto | `mes` | Sim | `yyyy-MM` |
| Acerto | `motoristaId` | Não | ID de motorista existente |

Datas dos limites são inclusivas. Não enviar parâmetros opcionais como `null`, `undefined`, `0` ou string vazia. IDs selecionados são os IDs das pessoas, conforme os cadastros existentes.

**Inadimplência:** totaliza o saldo restante após todas as parcelas; `valor` continua sendo o nominal. A integração das mensalidades está em **GUIA_CONTAS_RECEBER_FRONTEND.md**. Posição atual, considerando vencimento estritamente anterior ao dia atual em São Paulo. Uma conta vencendo hoje ainda não é inadimplente. Alunos arquivados com débitos continuam presentes e devem receber a indicação “Arquivado”. A seleção de datas filtra vencimentos; não reconstrói o status financeiro passado.

**Fluxo:** exige saldo base cadastrado. Usa as datas reais de recebimento e pagamento, não o vencimento. A lista já vem ordenada por data, tipo e ID, com entradas antes das saídas na mesma data. Não reagrupar de modo que o saldo acumulado perca essa ordem. Valores pagos parcialmente entram pelo valor efetivo.

**Acerto:** o mês seleciona a competência do lançamento. Exemplo: salário da competência setembro pode vencer em outubro e ter pagamentos em novembro; aparece no acerto de setembro com a posição atual dos pagamentos. Mostrar a data de referência junto do resultado.

Listas vazias são resultados válidos. Permitir download do PDF vazio, que contém uma mensagem explicativa. Exibir os avisos de revisão; não ocultar silenciosamente divergências.

## 4. Contratos TypeScript completos

Criar `models/relatorios.models.ts`:

```ts
export type StatusFinanceiro = 'PENDENTE' | 'PAGO' | 'ATRASADO';
export type ClassificacaoConta = 'SALARIO' | 'DESPESA';
export type FormaPagamento = 'PIX' | 'DINHEIRO' | 'CARTAO' | 'BOLETO' | 'TRANSFERENCIA';

export interface ReferenciaRelatorio {
  dataReferencia: string; // yyyy-MM-dd em America/Sao_Paulo
  geradoEm: string; // ISO com offset
  avisos: string[];
}
export interface CobrancaRelatorio {
  contaId: number;
  descricao: string | null;
  vencimento: string;
  valor: number | null;
  diasAtraso: number;
  competencia: string | null;
  valorRecebido: number;
  saldoRestante: number;
  recebimentoParcial: boolean;
  avisos: string[];
}
export interface AlunoInadimplente {
  alunoId: number;
  nome: string | null;
  matricula: string | null;
  telefone: string | null;
  arquivado: boolean;
  cobrancas: CobrancaRelatorio[];
  total: number;
}
export interface Inadimplencia extends ReferenciaRelatorio {
  alunoId: number | null;
  vencimentoInicio: string | null;
  vencimentoFim: string | null;
  alunos: AlunoInadimplente[];
  quantidadeCobrancas: number;
  totalGeral: number;
}
export interface SaldoBase {
  dataReferencia: string;
  saldo: number;
  versao: number;
}
export interface SaldoBaseRequest {
  dataReferencia: string;
  saldo: number;
  versao: number | null; // null somente no primeiro cadastro
}
export interface MovimentoCaixa {
  id: number; // ID do recebimento OU pagamento, diferenciado por tipo
  contaId: number | null;
  data: string;
  tipo: 'ENTRADA' | 'SAIDA';
  descricao: string | null;
  pessoa: string | null;
  valor: number;
  saldoAcumulado: number;
}
export interface FluxoCaixa extends ReferenciaRelatorio {
  dataInicio: string;
  dataFim: string;
  saldoBase: SaldoBase;
  saldoInicial: number;
  totalEntradas: number;
  totalSaidas: number;
  saldoFinal: number;
  movimentos: MovimentoCaixa[];
}
export interface LancamentoAcerto {
  contaId: number;
  descricao: string | null;
  competencia: string;
  vencimento: string | null;
  classificacao: ClassificacaoConta;
  valor: number | null;
  valorPago: number;
  saldoRestante: number;
  pagamentoParcial: boolean;
  avisos: string[];
}
export interface AcertoMotorista {
  motoristaId: number;
  nome: string | null;
  lancamentos: LancamentoAcerto[];
  totalSalarios: number;
  totalDespesas: number;
  totalPrevisto: number;
  totalPago: number;
  saldoAPagar: number;
}
export interface Acerto extends ReferenciaRelatorio {
  mes: string;
  motoristaId: number | null;
  motoristas: AcertoMotorista[];
  totalPrevisto: number;
  totalPago: number;
  saldoAPagar: number;
}
export interface FiltroInadimplencia {
  alunoId?: number;
  vencimentoInicio?: string;
  vencimentoFim?: string;
}
export interface FiltroFluxo { dataInicio: string; dataFim: string; }
export interface FiltroAcerto { mes: string; motoristaId?: number; }

export interface PessoaConta {
  id: number; nome: string | null; tipoPessoa: 'ADMIN' | 'ALUNO' | 'MOTORISTA' | null;
}
export interface ContaPagarResponse {
  id: number;
  descricao: string | null;
  valor: number | null;
  dataVencimento: string | null;
  status: StatusFinanceiro | null;
  motorista: PessoaConta | null; // vínculo genérico legado com Pessoa
  competencia: string | null;
  classificacao: ClassificacaoConta | null;
  valorPago: number;
  saldoRestante: number;
  pagamentoParcial: boolean;
  avisos: string[];
}
export interface ContaPagarRequest {
  id?: number;
  descricao: string;
  valor: number;
  dataVencimento: string;
  motorista: { id: number } | null;
  competencia?: string;
  classificacao?: ClassificacaoConta;
  status?: StatusFinanceiro; // não usar para efetuar ou desfazer quitação
}
export interface ReciboPagamento {
  id: number;
  valor: number;
  dataPagamento: string;
  formaPagamento: FormaPagamento | null;
  nomeMotorista: string | null;
  descricaoConta: string | null;
}
```

Totais usam `BigDecimal` no backend e chegam como números JSON. O frontend formata os valores recebidos; não recalcula a contabilidade somando números de ponto flutuante. Campo monetário `null` significa dado ausente e deve aparecer como “Não informado”.

## 5. Service HTTP e download

Criar `service/relatorios-service.ts`. Os caminhos abaixo consideram a estrutura proposta:

```ts
import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { mensagemApi } from '../../../core/auth/utils/api-error';
import {
  Acerto, Inadimplencia, FluxoCaixa, FiltroAcerto, FiltroFluxo,
  FiltroInadimplencia, SaldoBase, SaldoBaseRequest,
} from '../models/relatorios.models';

@Injectable({ providedIn: 'root' })
export class RelatoriosService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  private params(filtro: object): HttpParams {
    let params = new HttpParams();
    for (const [chave, valor] of Object.entries(filtro)) {
      if (valor !== undefined && valor !== null && valor !== '') {
        params = params.set(chave, String(valor));
      }
    }
    return params;
  }
  inadimplencia(filtro: FiltroInadimplencia): Observable<Inadimplencia> {
    return this.http.get<Inadimplencia>(`${this.base}/relatorios/inadimplencia`, { params: this.params(filtro) });
  }
  fluxo(filtro: FiltroFluxo): Observable<FluxoCaixa> {
    return this.http.get<FluxoCaixa>(`${this.base}/relatorios/fluxo-caixa`, { params: this.params(filtro) });
  }
  acerto(filtro: FiltroAcerto): Observable<Acerto> {
    return this.http.get<Acerto>(`${this.base}/relatorios/acerto-motoristas`, { params: this.params(filtro) });
  }
  pdf(tipo: 'inadimplencia' | 'fluxo-caixa' | 'acerto-motoristas', filtro: object): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/relatorios/${tipo}/pdf`, {
      params: this.params(filtro), responseType: 'blob', observe: 'response',
    });
  }
  saldoBase(): Observable<SaldoBase> {
    return this.http.get<SaldoBase>(`${this.base}/financeiro/saldo-base`);
  }
  salvarSaldoBase(entrada: SaldoBaseRequest): Observable<SaldoBase> {
    return this.http.put<SaldoBase>(`${this.base}/financeiro/saldo-base`, entrada);
  }
}

export function baixarPdf(resposta: HttpResponse<Blob>): void {
  if (!resposta.body) throw new Error('O servidor retornou um arquivo vazio.');
  const cabecalho = resposta.headers.get('Content-Disposition') ?? '';
  const recebido = /filename="?([^";]+)"?/i.exec(cabecalho)?.[1] ?? 'relatorio.pdf';
  const nome = recebido.replace(/[\\/:*?"<>|]/g, '_');
  const url = URL.createObjectURL(resposta.body);
  const link = document.createElement('a');
  link.href = url; link.download = nome;
  document.body.appendChild(link); link.click(); link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export async function mensagemErroDownload(erro: unknown): Promise<string> {
  if (erro instanceof HttpErrorResponse && erro.error instanceof Blob) {
    try {
      const problema = JSON.parse(await erro.error.text()) as { detail?: string; title?: string };
      return problema.detail ?? problema.title ?? 'Não foi possível gerar o relatório.';
    } catch { return 'Não foi possível gerar o relatório. Tente novamente.'; }
  }
  return mensagemApi(erro);
}
```

O backend já permite `Content-Disposition` no CORS. A requisição continua usando o interceptor de autenticação. Evitar abrir o endpoint diretamente com `window.open(url)`, pois essa navegação não passa pelo interceptor JWT.

## 6. Página de relatórios

Usar três opções: “Inadimplência de alunos”, “Fluxo de caixa” e “Acerto de motoristas”. Selecionar inadimplência inicialmente. Para fluxo e acerto, iniciar os filtros no mês atual de São Paulo. Carregar somente o relatório escolhido quando o usuário clicar em Consultar.

Organizar a página em:

1. Título e descrição curta.
2. Seletor do relatório e filtros específicos.
3. Botões Consultar e Baixar PDF, com carregamentos independentes.
4. Resumo de valores retornados pelo backend.
5. Tabela detalhada e avisos de revisão.
6. Configuração do saldo base, acessível no fluxo de caixa.

Ao mudar tipo ou filtros, limpar o resultado anterior e desabilitar ações que dependam dele. Durante uma consulta, desabilitar alterações dos filtros para evitar que o resultado seja associado a outros valores. O download pode ser feito com os filtros validados, mesmo quando o resultado é vazio.

Exemplo funcional de componente para inadimplência, que pode ser incorporado à página com as outras duas consultas:

```ts
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { MessageModule } from 'primeng/message';
import { Inadimplencia, FiltroInadimplencia } from '../../models/relatorios.models';
import { RelatoriosService, baixarPdf, mensagemErroDownload } from '../../service/relatorios-service';
import { mensagemApi } from '../../../../core/auth/utils/api-error';

@Component({
  selector: 'app-relatorios', standalone: true,
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe, ButtonModule, TableModule, MessageModule],
  templateUrl: './relatorios.html', styleUrl: './relatorios.css',
})
export class Relatorios {
  private readonly api = inject(RelatoriosService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  readonly carregando = signal(false);
  readonly baixando = signal(false);
  readonly erro = signal('');
  readonly resultado = signal<Inadimplencia | null>(null);
  readonly form = this.fb.group({
    alunoId: this.fb.control<number | null>(null),
    vencimentoInicio: [''], vencimentoFim: [''],
  });
  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.resultado.set(null));
  }
  private filtro(): FiltroInadimplencia {
    const v = this.form.getRawValue();
    return {
      alunoId: v.alunoId ?? undefined,
      vencimentoInicio: v.vencimentoInicio || undefined,
      vencimentoFim: v.vencimentoFim || undefined,
    };
  }
  private validar(): boolean {
    const f = this.filtro();
    if (f.vencimentoInicio && f.vencimentoFim && f.vencimentoInicio > f.vencimentoFim) {
      this.erro.set('A data inicial deve ser anterior ou igual à final.'); return false;
    }
    return this.form.valid;
  }
  consultar(): void {
    if (this.carregando() || this.baixando() || !this.validar()) return;
    this.erro.set(''); this.resultado.set(null); this.carregando.set(true); this.form.disable({ emitEvent: false });
    this.api.inadimplencia(this.filtro()).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => { this.carregando.set(false); this.form.enable({ emitEvent: false }); }),
    ).subscribe({ next: r => this.resultado.set(r), error: e => this.erro.set(mensagemApi(e)) });
  }
  baixar(): void {
    if (this.carregando() || this.baixando() || !this.validar()) return;
    this.erro.set(''); this.baixando.set(true);
    this.api.pdf('inadimplencia', this.filtro()).pipe(
      takeUntilDestroyed(this.destroyRef), finalize(() => this.baixando.set(false)),
    ).subscribe({
      next: r => baixarPdf(r),
      error: async e => this.erro.set(await mensagemErroDownload(e)),
    });
  }
}
```

`relatorios.html` do exemplo:

```html
<section class="relatorios">
  <h1>Relatórios financeiros</h1>
  <p>Consulte os lançamentos e baixe o demonstrativo em PDF.</p>
  <form [formGroup]="form" (ngSubmit)="consultar()" class="filtros">
    <label>Aluno ID (opcional)<input type="number" min="1" formControlName="alunoId" /></label>
    <label>Vencimento inicial<input type="date" formControlName="vencimentoInicio" /></label>
    <label>Vencimento final<input type="date" formControlName="vencimentoFim" /></label>
    <p-button type="submit" label="Consultar" [loading]="carregando()" [disabled]="baixando()" />
    <p-button type="button" label="Baixar PDF" icon="pi pi-file-pdf" [loading]="baixando()"
      [disabled]="carregando()" (onClick)="baixar()" />
  </form>
  @if (erro()) { <p-message severity="error">{{ erro() }}</p-message> }
  @if (resultado(); as r) {
    <p>Posição: {{ r.dataReferencia | date:'dd/MM/yyyy':'UTC' }}</p>
    <p>{{ r.alunos.length }} alunos · {{ r.quantidadeCobrancas }} cobranças ·
      Total: {{ r.totalGeral | currency:'BRL':'symbol':'1.2-2':'pt-BR' }}</p>
    @for (aviso of r.avisos; track $index) { <p-message severity="warn">{{ aviso }}</p-message> }
    @for (aluno of r.alunos; track aluno.alunoId) {
      <h2>{{ aluno.nome ?? 'Não informado' }} @if (aluno.arquivado) { <small>Arquivado</small> }</h2>
      <p>Matrícula: {{ aluno.matricula ?? 'Não informada' }} · Telefone: {{ aluno.telefone ?? 'Não informado' }}</p>
      <p-table [value]="aluno.cobrancas" [stripedRows]="true">
        <ng-template #header><tr><th>Descrição</th><th>Vencimento</th><th>Dias de atraso</th><th>Valor</th></tr></ng-template>
        <ng-template #body let-c>
          <tr><td>{{ c.descricao }}</td><td>{{ c.vencimento | date:'dd/MM/yyyy':'UTC' }}</td>
            <td>{{ c.diasAtraso }}</td><td>{{ c.saldoRestante | currency:'BRL':'symbol':'1.2-2':'pt-BR' }}</td></tr>
        </ng-template>
      </p-table>
      <p>Total do aluno: {{ aluno.total | currency:'BRL':'symbol':'1.2-2':'pt-BR' }}</p>
    } @empty { <p>Nenhuma mensalidade vencida e não paga para os filtros selecionados.</p> }
  }
</section>
```

No produto final, trocar o campo numérico do exemplo por `p-select` com os cadastros de alunos existentes. Não limitar a seleção aos ativos: arquivados podem ter débitos. Manter o ID escolhido mesmo quando seu nome precisar ser exibido a partir do resultado.

Para o fluxo, usar `api.fluxo({dataInicio, dataFim})`, quatro resumos (`saldoInicial`, `totalEntradas`, `totalSaidas`, `saldoFinal`) e tabela Data / Tipo / Descrição / Pessoa / Entrada / Saída / Saldo. Usar chave composta `tipo + ':' + id`; recebimento e pagamento podem ter IDs iguais.

Para acerto, usar `api.acerto({mes, motoristaId})`, campo `<input type="month">`, seleção opcional de motorista e uma tabela por motorista com Descrição / Classificação / Vencimento / Previsto / Pago / Saldo. Exibir os avisos globais e os avisos de cada lançamento. Os três totais gerais já vêm calculados.

O exemplo é um ponto de integração, não a implementação das três telas. Completar as outras visualizações seguindo os contratos acima. Registrar o locale `pt-BR` uma única vez no bootstrap, se ainda não estiver registrado:

```ts
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
registerLocaleData(localePt);
```

## 7. Saldo base: primeiro cadastro e alteração

`GET /financeiro/saldo-base` retorna 404 até o primeiro cadastro. Somente nesse contexto, tratar 404 como “Saldo base não configurado” e abrir o formulário. Um 404 em outro endpoint não deve abrir esse formulário automaticamente.

Primeiro cadastro:

```http
PUT /financeiro/saldo-base
Content-Type: application/json

{"dataReferencia":"2026-10-01","saldo":2500.00,"versao":null}
```

Resposta:

```json
{"dataReferencia":"2026-10-01","saldo":2500.00,"versao":0}
```

Ao editar, enviar a versão retornada pela última consulta. O backend incrementa a versão. Se outra pessoa alterar antes, a API retorna 409; recarregar e solicitar nova revisão do formulário, sem repetir a gravação automaticamente.

Explicação visível ao administrador: **“Informe o saldo existente na abertura desta data, antes das movimentações daquele dia.”** O saldo pode ser negativo. Validar valor com até duas casas decimais e a data obrigatória.

Exemplo: saldo base de R$ 2.500 em 01/10; recebimentos de R$ 300 e pagamentos de R$ 100 entre 01/10 e 09/10. O saldo inicial do relatório começando em 10/10 será R$ 2.700. Os movimentos de 10/10 aparecerão na tabela, não entrarão novamente no saldo inicial.

Não permitir consultar fluxo com início anterior à data base. Alterar a data/saldo base muda os próximos cálculos; após salvar, invalidar o resultado em tela e pedir nova consulta.

## 8. Atualizações na tela Contas a pagar

Os endpoints de CRUD continuam os mesmos. A resposta agora é `ContaPagarResponse`, com `motorista` resumido a `id`, `nome` e `tipoPessoa`. CPF, CNH e salário cadastral não são enviados nessa resposta. Usar o cadastro de motoristas para carregar opções, não os dados internos da conta.

Exemplo de criação de salário com competência diferente do vencimento:

```json
{
  "descricao":"Salário - José da Conceição",
  "valor":3000,
  "dataVencimento":"2026-11-05",
  "motorista":{"id":42},
  "competencia":"2026-10",
  "classificacao":"SALARIO"
}
```

Competência e vencimento são obrigatórios para salários; o beneficiário precisa ser motorista. O backend rejeita novo salário duplicado por motorista/competência. Para despesas, a classificação omitida assume `DESPESA`; competência omitida é inicializada pelo mês do vencimento, preservando clientes antigos. A interface nova deve enviar competência explicitamente.

Despesas podem continuar sem pessoa vinculada ou com uma pessoa permitida pelo sistema. Não restringir o vínculo genérico legado de todas as despesas a motoristas.

Na lista e no modal:

- Mostrar valor original, total pago e saldo restante.
- O preenchimento inicial do pagamento deve usar `conta.saldoRestante`, não `conta.valor`.
- Mostrar “Parcial” quando `pagamentoParcial=true`. Não acrescentar `PARCIAL` ao enum do backend.
- Definir ação Pagar por saldo positivo, respeitando os avisos de regularização.
- Depois de pagar, recarregar contas e invalidar relatórios/dashboard em cache.
- Não enviar `status=PAGO` para quitar; não alterar status manualmente para reabrir.
- Desabilitar exclusão e alteração de valor/beneficiário quando houver pagamento ou quitação histórica; o backend também verifica essas regras.
- Classificação, competência e descrição podem ser corrigidas administrativamente; salários continuam sujeitos à validação de duplicidade.

## 9. Pagamentos parciais, idempotência e comprovantes

O endpoint existente permanece:

```http
POST /pagamentos/realizar/123?formaPagamento=PIX&valorPago=600.00
Idempotency-Key: 3d639dfb-0b3c-48a7-8fc5-9571ed38517d
```

Sem `valorPago`, paga o saldo restante. O valor deve ser positivo, não ultrapassar o saldo e ter até duas casas decimais. O backend define a data atual em São Paulo.

O header é opcional para clientes antigos, mas o novo frontend deve utilizá-lo. Gerar um UUID com `crypto.randomUUID()` ao confirmar uma intenção de pagamento. Desabilitar o botão enquanto a requisição está pendente. Em falha de rede ou resposta incerta, repetir com **a mesma chave e os mesmos parâmetros**, inclusive mantendo a omissão de `valorPago` se ele foi omitido inicialmente. A repetição retorna o pagamento original, mesmo se a conta já tiver sido quitada.

Se o usuário alterar valor, conta ou forma de pagamento, isso representa outra intenção e exige nova chave. Não gerar uma nova chave automaticamente a cada tentativa de rede. Uma chave usada com outros parâmetros recebe 409. Guardar a chave pendente em `sessionStorage` junto da conta/valor/forma permite recuperar a tentativa após recarregar a página; remover após resposta conclusiva e atualização da tela.

Adaptação do service existente:

```ts
realizarPagamento(contaId: number, formaPagamento: FormaPagamento, valorPago: number | undefined, chave: string) {
  let params = new HttpParams().set('formaPagamento', formaPagamento);
  if (valorPago !== undefined) params = params.set('valorPago', String(valorPago));
  return this.http.post(`${this.apiUrl}/realizar/${contaId}`, null, {
    params, headers: { 'Idempotency-Key': chave },
  });
}
```

A resposta desse POST preserva o objeto de pagamento existente (`id`, `valor`, `dataPagamento`, `formaPagamento`, `contaPagar`). Não usar o objeto aninhado para calcular saldo; recarregar a conta pelo CRUD, que retorna a posição financeira calculada.

Comprovantes:

- `GET /pagamentos/recibo/conta/{contaId}` retorna o mais recente por data/ID; continua retornando 404 se nenhum existir.
- `GET /pagamentos/conta/{contaId}` retorna `ReciboPagamento[]`, do mais recente para o mais antigo; conta existente sem pagamentos retorna `[]`; conta inexistente retorna 404.

O modal deve listar os pagamentos e permitir abrir cada comprovante retornado. O recibo atual continua disponível para clientes antigos, mas não representa sozinho toda a quitação de uma conta com vários pagamentos.

## 10. Avisos de dados antigos

O campo `avisos` dos relatórios contém mensagens legíveis. Em contas e lançamentos, os avisos são códigos:

| Código | Apresentação sugerida |
|---|---|
| `VALOR_INVALIDO` | Regularizar valor do lançamento |
| `COMPETENCIA_AUSENTE` | Informar competência |
| `VENCIMENTO_AUSENTE` | Informar vencimento |
| `PAGAMENTO_SUPERIOR_A_CONTA` | Pagamentos antigos superiores ao valor da conta |
| `PAGAMENTO_NEGATIVO_LEGADO` | Pagamento antigo negativo exige revisão |
| `QUITACAO_SEM_COMPROVANTE` | Conta antiga quitada sem comprovante registrado |
| `STATUS_DIVERGENTE` | Status antigo diverge dos pagamentos registrados |

Após a V9, contas antigas com comprovantes positivos insuficientes são reabertas pelo saldo efetivamente comprovado. A migração não muda os valores dos comprovantes. Uma conta quitada sem comprovantes permanece quitada, recebe aviso e não cria saída fictícia no fluxo.

Por isso, `totalPrevisto - totalPago` pode diferir de `saldoAPagar` em dados legados sem comprovantes ou com pagamentos excessivos. Mostrar os três totais recebidos e os avisos, sem tentar “corrigir” o saldo no navegador.

Contas sem competência não entram em um mês arbitrário: o acerto identifica seus IDs nos avisos para regularização. A classificação histórica de salários usa a descrição exata gerada pelo sistema e vínculo com motorista; revisar lançamentos antigos que tenham sido renomeados ou classificados manualmente.

## 11. Exemplos de respostas

Inadimplência:

```json
{
  "dataReferencia":"2026-10-16","geradoEm":"2026-10-16T12:00:00-03:00",
  "alunoId":null,"vencimentoInicio":null,"vencimentoFim":null,
  "alunos":[{"alunoId":7,"nome":"João Álvares","matricula":"2026001","telefone":"(18) 99999-0000","arquivado":false,
    "cobrancas":[{"contaId":31,"descricao":"Mensalidade","vencimento":"2026-10-10","valor":125.50,"diasAtraso":6,"competencia":"2026-10","valorRecebido":0,"saldoRestante":125.50,"recebimentoParcial":false,"avisos":[]}],"total":125.50}],
  "quantidadeCobrancas":1,"totalGeral":125.50,"avisos":[]
}
```

Fluxo:

```json
{
  "dataReferencia":"2026-10-16","geradoEm":"2026-10-16T12:00:00-03:00",
  "dataInicio":"2026-10-16","dataFim":"2026-10-16",
  "saldoBase":{"dataReferencia":"2026-10-01","saldo":2500,"versao":0},
  "saldoInicial":2500,"totalEntradas":0,"totalSaidas":600,"saldoFinal":1900,
  "movimentos":[{"id":12,"contaId":123,"data":"2026-10-16","tipo":"SAIDA","descricao":"Salário - José da Conceição","pessoa":"José da Conceição","valor":600,"saldoAcumulado":1900}],
  "avisos":[]
}
```

Acerto:

```json
{
  "dataReferencia":"2026-10-16","geradoEm":"2026-10-16T12:00:00-03:00","mes":"2026-10","motoristaId":null,
  "motoristas":[{"motoristaId":42,"nome":"José da Conceição",
    "lancamentos":[{"contaId":123,"descricao":"Salário - José da Conceição","competencia":"2026-10","vencimento":"2026-11-05","classificacao":"SALARIO","valor":3000,"valorPago":600,"saldoRestante":2400,"pagamentoParcial":true,"avisos":[]}],
    "totalSalarios":3000,"totalDespesas":0,"totalPrevisto":3000,"totalPago":600,"saldoAPagar":2400}],
  "totalPrevisto":3000,"totalPago":600,"saldoAPagar":2400,"avisos":[]
}
```

Os exemplos ilustram a estrutura real dos DTOs, com dados fictícios. Datas/IDs do ambiente variam.

## 12. Erros, datas e segurança

Erros seguem `application/problem+json`, com `status`, `title` e `detail`. Quando há validação de formulário, pode haver `errors` com mensagens por campo. O PDF também retorna esse JSON em falhas; como a requisição usa `responseType: 'blob'`, é necessário o helper da seção 5.

| Status | Tratamento |
|---|---|
| 400 | Corrigir filtros, competência, datas, valor ou chave inválida |
| 401 | Fluxo de autenticação já existente |
| 403 | Tela de acesso negado já existente |
| 404 | Cadastro inexistente; no GET de saldo base, oferecer primeiro cadastro |
| 409 | Recarregar registro em conflito; não repetir pagamento com chave nova sem conferir |
| 500 | Informar falha de geração, permitindo nova tentativa |

Datas de negócio são strings `yyyy-MM-dd`. Evitar `new Date(valor).toLocaleDateString()` sem fuso explícito, que pode mostrar o dia anterior. No template, usar `date:'dd/MM/yyyy':'UTC'` para datas sem horário. Para filtros nativos date/month, enviar diretamente a string do controle. Para a data/hora de emissão, formatar com o offset/horário de São Paulo.

Renderizar nomes e descrições por interpolação Angular. Não usar `[innerHTML]` em dados do relatório. Tratar a informação financeira como conteúdo administrativo e não guardar relatórios ou PDFs em cache compartilhado entre usuários.

## 13. Checklist de implementação e testes

- [ ] Rota/menu visíveis somente para ADMIN, com guards existentes.
- [ ] Três consultas e três downloads usando os mesmos filtros.
- [ ] Filtros opcionais omitidos corretamente e períodos invertidos bloqueados.
- [ ] Mês no formato `yyyy-MM`, sem confundir competência com vencimento.
- [ ] Nome do PDF obtido de `Content-Disposition`; URL temporária liberada.
- [ ] Erros de download recebidos como Blob convertidos em mensagens.
- [ ] Formulário de saldo base trata primeiro cadastro, atualização e conflito de versão.
- [ ] Datas de negócio não retrocedem um dia no navegador.
- [ ] Tabelas com valores e totais do backend, sem recomputar saldo.
- [ ] Estado vazio, carregamento, avisos e descrições longas testados.
- [ ] Alunos arquivados com dívida continuam visíveis.
- [ ] Pagamento parcial mantém saldo, quitação posterior atualiza a lista e comprovantes.
- [ ] Nova tentativa de uma operação incerta usa a mesma chave e parâmetros.
- [ ] Confirmação não dispara duas requisições por duplo clique.
- [ ] Edição/exclusão de conta com pagamento respeita as restrições.
- [ ] Recorrência salarial explicada como mensal; dia 31 ajusta fevereiro e retorna ao dia 31.
- [ ] Testes Angular e `npm run build` executados no projeto frontend depois da integração.

## 14. Backend e homologação

Subir o backend com JDK 21. A V9 é aplicada pelo Flyway na inicialização normal; não executar SQL manual no frontend. Os templates são compilados na inicialização e os dados são fornecidos pelo serviço Java, sem SQL nos `.jrxml`.

Os testes de relatórios usam dados fictícios e produzem exemplos em `target/relatorios-exemplos/`. Testes PostgreSQL são habilitados por `RELATORIOS_TEST_DB_URL`, `RELATORIOS_TEST_DB_USER` e `RELATORIOS_TEST_DB_PASSWORD`, criando e removendo apenas schemas próprios `test_relatorios_*`.

A configuração real de saldo base deve ser feita pelo administrador com dados da associação. A entrega do backend não representa cadastro desse saldo nem implantação da interface Angular.
