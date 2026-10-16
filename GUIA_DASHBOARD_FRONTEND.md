# Dashboard por papel — guia de implementação no Angular

> Atualização de 16/10/2026: cobranças do aluno agora incluem competência, valorRecebido, saldoRestante, recebimentoParcial e avisos. Totais financeiros de contas a receber usam saldo restante. Atualize o componente compartilhado de pagamento conforme **GUIA_CONTAS_RECEBER_FRONTEND.md**; os campos originais permanecem.

Referência: backend implementado em 10/10/2026. Este arquivo orienta a próxima entrega do frontend; nenhum arquivo Angular foi alterado nesta entrega.

## 1. Antes de começar

A página nova será **Início**, em `/inicio`. “Meu perfil” continua em `/perfil`. O backend já fornece:

| Consulta | Papel exclusivo | Conteúdo |
|---|---|---|
| GET `/dashboard/aluno` | ALUNO | faculdade, transporte relacionado e cobrança |
| GET `/dashboard/motorista` | MOTORISTA | somente suas atribuições |
| GET `/dashboard/admin?mes=2026-10` | ADMIN | movimentações mensais, contas abertas e operação |

O JWT identifica a conta. Não enviar alunoId, motoristaId, pessoaId ou faculdadeId. ADMIN também não pode usar os endpoints exclusivos dos outros papéis. O mês é opcional apenas no administrativo; omitido significa o mês atual em São Paulo.

As consultas não geram cobranças, não renovam matrícula, não pagam contas nem alteram viagens. Não há tabela ou migration nova para o dashboard.

### Organização conferida no frontend atual

- Autenticação: `src/app/core/auth/service/auth.service.ts`.
- Guards: `src/app/core/auth/guards/auth.guards.ts`.
- Erros: `src/app/core/auth/utils/api-error.ts`, função `mensagemApi`.
- Rotas: `src/app/app.routes.ts`.
- Menu: `src/app/components/sidebar/components/sidebar/sidebar.ts`.
- Matrículas: `src/app/components/conta-receber/components/minhas-matriculas/`.
- Pagamentos/recibos: `src/app/components/conta-receber/service/recebimento-service.ts`.
- Projeto Angular 21 standalone e PrimeNG; manter o interceptor JWT já registrado.

Estrutura sugerida, seguindo as pastas existentes:

```text
src/app/components/dashboard/
  models/dashboard.models.ts
  service/dashboard-service.ts
  components/inicio/inicio.ts
  components/dashboard-aluno/dashboard-aluno.ts
  components/dashboard-aluno/dashboard-aluno.html
  components/dashboard-motorista/dashboard-motorista.ts
  components/dashboard-admin/dashboard-admin.ts
  components/agenda-transporte/agenda-transporte.ts
src/app/components/conta-receber/components/pagamento-matricula/
  pagamento-matricula.ts
  pagamento-matricula.html
```

Não carregar os três endpoints ao mesmo tempo: criar apenas o componente correspondente ao papel autenticado.

## 2. Contratos TypeScript completos

Criar `dashboard.models.ts`. Os nomes abaixo são os nomes exatos do JSON. Tipos com `null` contemplam cadastros legados incompletos; não inventar valores para eles.

```ts
export type DiaSemana = 'SEGUNDA' | 'TERCA' | 'QUARTA' | 'QUINTA' | 'SEXTA';
export type StatusMatricula = 'ATIV0' | 'INATIV0' | 'PENDENTE'; // Algarismo ZERO.
export type StatusOnibus = 'OPERANDO' | 'MANUTENCAO' | 'QUEBRADO' | 'INATIVO';
export type StatusFinanceiro = 'PENDENTE' | 'PAGO' | 'ATRASADO';
export type NivelUrgencia = 'BAIXA' | 'MEDIA' | 'ALTA';
export type StatusChamado = 'ABERTO' | 'EM_ANDAMENTO' | 'RESOLVIDO';
export type SituacaoCobranca =
  | 'PAGO' | 'VENCIDA' | 'VENCE_HOJE' | 'A_VENCER' | 'SEM_VENCIMENTO';

export interface ReferenciaDashboard {
  dataReferencia: string; // yyyy-MM-dd, data de negócio em America/Sao_Paulo.
  geradoEm: string; // ISO com offset, ex.: 2026-10-12T12:00:00-03:00.
}
export interface AlunoResumo {
  pessoaId: number;
  nome: string | null;
  matricula: string | null;
  statusMatricula: StatusMatricula | null;
}
export interface PessoaResumo { pessoaId: number; nome: string | null; }
export interface FaculdadeResumo {
  id: number; nome: string | null; endereco: string | null;
}
export interface PontoResumo {
  id: number; descricao: string | null; ordemParada: number | null;
}
export interface RotaResumo {
  id: number; nome: string | null; descricao: string | null;
  pontosEmbarque: PontoResumo[];
}
export interface GradeResumo {
  id: number; diaSemana: DiaSemana | null; descricao: string | null;
}
export interface OnibusResumo {
  id: number; numeroIdentificacao: string | null; placa: string | null;
  modelo: string | null; capacidade: number | null;
  statusOnibus: StatusOnibus | null;
}
export interface ViagemResumo {
  id: number;
  grade: GradeResumo | null;
  dataOcorrencia: string | null;
  rota: RotaResumo | null;
  onibus: OnibusResumo | null;
  motorista: PessoaResumo | null;
  faculdades: FaculdadeResumo[];
  onibusDisponivel: boolean;
  avisos: string[];
}
export interface TransporteResumo {
  viagensHoje: ViagemResumo[];
  agendaSemanal: ViagemResumo[];
  programacaoSemGrade: ViagemResumo[];
  rotas: RotaResumo[];
}
export interface CobrancaResumo {
  id: number; descricao: string | null; valor: number | null;
  dataVencimento: string | null;
  status: StatusFinanceiro | null;
  situacao: SituacaoCobranca;
  diasParaVencimento: number | null;
}
export interface TotalFinanceiro { quantidade: number; valor: number; }
export interface FinanceiroAluno {
  mensalidadeCadastro: number | null;
  cobrancaPrincipal: CobrancaResumo | null;
  emAberto: TotalFinanceiro;
  vencidas: TotalFinanceiro;
}
export interface AlunoDashboard extends ReferenciaDashboard {
  aluno: AlunoResumo;
  faculdade: FaculdadeResumo | null;
  transporte: TransporteResumo;
  financeiro: FinanceiroAluno;
  avisos: string[];
}
export interface MotoristaDashboard extends ReferenciaDashboard {
  motorista: PessoaResumo;
  transporte: TransporteResumo;
  avisos: string[];
}
export interface ResumoContas {
  emAberto: TotalFinanceiro;
  vencidas: TotalFinanceiro;
  vencemEmSeteDias: TotalFinanceiro;
}
export interface MovimentoMensal {
  mes: string; recebido: number; pago: number; saldoMovimentacoes: number;
}
export interface PendenciaFinanceira {
  id: number; descricao: string | null; valor: number | null;
  dataVencimento: string | null;
  status: StatusFinanceiro | null;
  situacao: SituacaoCobranca;
  nomePessoa: string | null;
}
export interface FinanceiroAdmin {
  recebidoNoMes: number; pagoNoMes: number; saldoMovimentacoes: number;
  contasReceber: ResumoContas; contasPagar: ResumoContas;
  serieMensal: MovimentoMensal[];
  pendenciasReceber: PendenciaFinanceira[];
  pendenciasPagar: PendenciaFinanceira[];
}
export interface AlunosIndicadores {
  total: number; ativos: number; inativos: number; pendentes: number; semStatus: number;
}
export interface OnibusIndicadores {
  total: number; operando: number; manutencao: number;
  quebrado: number; inativo: number; semStatus: number;
}
export interface ChamadosIndicadores {
  abertos: number; emAndamento: number; prioridadeAlta: number;
}
export interface ChamadoPendente {
  id: number; descricao: string; dataAbertura: string;
  urgencia: NivelUrgencia; status: StatusChamado; onibusId: number;
  numeroIdentificacao: string | null; placa: string | null;
  solicitanteNome: string | null;
}
export interface OperacaoResumo {
  alunos: AlunosIndicadores; onibus: OnibusIndicadores;
  viagensHoje: number; viagensSemProgramacao: number;
  chamados: ChamadosIndicadores; chamadosPendentes: ChamadoPendente[];
}
export interface AdminDashboard extends ReferenciaDashboard {
  mes: string; financeiro: FinanceiroAdmin; operacao: OperacaoResumo;
}
```

É possível reutilizar enums já existentes via imports em vez de redeclará-los. Não trocar `ATIV0` por `ATIVO` no contrato.

Valores monetários saem como números JSON, calculados com BigDecimal no backend. No frontend, formatar os totais recebidos; não recalcular contabilidade por somas de ponto flutuante.

## 3. Serviço HTTP

Criar `service/dashboard-service.ts`:

```ts
import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AlunoDashboard, MotoristaDashboard, AdminDashboard } from '../models/dashboard.models';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/dashboard`;

  aluno(): Observable<AlunoDashboard> {
    return this.http.get<AlunoDashboard>(`${this.url}/aluno`);
  }
  motorista(): Observable<MotoristaDashboard> {
    return this.http.get<MotoristaDashboard>(`${this.url}/motorista`);
  }
  admin(mes?: string): Observable<AdminDashboard> {
    const params = mes === undefined ? new HttpParams() : new HttpParams().set('mes', mes);
    return this.http.get<AdminDashboard>(`${this.url}/admin`, { params });
  }
}
```

Não adicionar Bearer manualmente: o interceptor existente já o faz somente para o backend. Não armazenar a resposta do dashboard em sessionStorage; ela pode ficar desatualizada após pagamento, alteração de grade, ônibus ou chamado.

## 4. Rota, login e menu

Adicionar a rota antes dos redirecionamentos de `app.routes.ts`:

```ts
{
  path: 'inicio',
  loadComponent: () =>
    import('./components/dashboard/components/inicio/inicio').then(m => m.Inicio),
  canActivate: [authGuard, roleGuard(['ADMIN', 'ALUNO', 'MOTORISTA'])],
},
// Preservar todas as rotas atuais, especialmente /perfil.
{ path: '', redirectTo: 'inicio', pathMatch: 'full' },
{ path: '**', redirectTo: 'inicio' },
```

Substituir os redirecionamentos finais atuais; não deixar dois curingas. Os guards existentes retornam UrlTree para login/sem-permissao.

Em `AuthService.destinoInicial()`:

```ts
destinoInicial(): string {
  return '/inicio';
}
```

Manter `redirecionarAposLogin(retorno)`, que preserva retorno interno seguro. Assim, login normal abre Início, mas uma tentativa válida de visitar outra página mantém seu destino. O guard dessa página continua verificando o papel. Não aceitar URLs externas como retorno.

O `guestGuard` atual manda quem já está logado para `/perfil`. Trocar apenas seu destino para `auth.destinoInicial()`:

```ts
return auth.token() ? router.createUrlTree([auth.destinoInicial()]) : true;
```

No menu, criar `const INICIO: ItemMenu = { label: 'Início', icon: 'pi pi-home', rota: '/inicio' }` e colocar `INICIO` antes de `PERFIL` nas três seções “Conta”. Não substituir o perfil nem ampliar os outros menus. A sidebar permanece oculta na tela de login.

Componente distribuidor `components/inicio/inicio.ts`:

```ts
import { Component, inject } from '@angular/core';
import { AuthService } from '../../../../core/auth/service/auth.service';
import { DashboardAluno } from '../dashboard-aluno/dashboard-aluno';
import { DashboardMotorista } from '../dashboard-motorista/dashboard-motorista';
import { DashboardAdmin } from '../dashboard-admin/dashboard-admin';

@Component({
  selector: 'app-inicio',
  standalone: true,
  imports: [DashboardAluno, DashboardMotorista, DashboardAdmin],
  template: `
    @switch (auth.papel()) {
      @case ('ALUNO') { <app-dashboard-aluno /> }
      @case ('MOTORISTA') { <app-dashboard-motorista /> }
      @case ('ADMIN') { <app-dashboard-admin /> }
      @default { <p>Sessão indisponível. Entre novamente.</p> }
    }
  `,
})
export class Inicio {
  readonly auth = inject(AuthService);
}
```

Os três componentes precisam dos selectors e nomes de classe usados acima. Importar classes não executa consultas: cada componente só consulta no próprio ciclo de vida, quando criado.

## 5. Carregamento e tratamento de falhas

Exemplo completo da classe do aluno, antes de adicionar a integração de pagamento:

```ts
import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { DashboardService } from '../../service/dashboard-service';
import { AlunoDashboard } from '../../models/dashboard.models';
import { mensagemApi } from '../../../../core/auth/utils/api-error';

@Component({
  selector: 'app-dashboard-aluno',
  standalone: true,
  imports: [CommonModule, RouterLink, ButtonModule, MessageModule, SkeletonModule],
  templateUrl: './dashboard-aluno.html',
})
export class DashboardAluno implements OnInit {
  private readonly service = inject(DashboardService);
  private readonly destroyRef = inject(DestroyRef);
  readonly dados = signal<AlunoDashboard | null>(null);
  readonly carregando = signal(false);
  readonly erro = signal<string | null>(null);

  ngOnInit(): void { this.carregar(); }

  carregar(): void {
    if (this.carregando()) return;
    this.carregando.set(true);
    this.erro.set(null);
    this.service.aluno().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.carregando.set(false)),
    ).subscribe({
      next: dados => this.dados.set(dados),
      error: (error: HttpErrorResponse) => this.erro.set(mensagemApi(error)),
    });
  }
}
```

O motorista usa o mesmo padrão com `MotoristaDashboard` e `service.motorista()`. O administrativo adiciona seleção mensal conforme a seção 9.

No HTML, a precedência precisa ser **carregando → erro → resposta válida**:

```html
<p-button label="Atualizar" icon="pi pi-refresh" [disabled]="carregando()"
          (onClick)="carregar()" />
@if (carregando()) {
  <p-skeleton height="12rem" />
} @else if (erro(); as mensagem) {
  <p-message severity="error">{{ mensagem }}</p-message>
} @else if (dados(); as painel) {
  <h1>Olá, {{ painel.aluno.nome ?? 'aluno' }}</h1>
  <p>Referência: {{ painel.dataReferencia | date:'dd/MM/yyyy':'UTC' }}</p>
  @for (aviso of painel.avisos; track $index) {
    <p-message severity="warn">{{ aviso }}</p-message>
  }
  <!-- Inserir os blocos das seções seguintes aqui. -->
}
```

Se uma atualização falhar, não exibir os dados antigos como se estivessem atuais; ocultá-los atrás do erro ou identificá-los explicitamente como desatualizados. Só mostrar “Sem registros” após uma resposta 200 realmente vazia. Não converter erro em `of([])` ou totais zero.

## 6. Regras da agenda: imprescindível

- `agendaSemanal` cobre a data de referência e os seis dias seguintes, em ordem de ocorrência e ID.
- `viagensHoje` é um subconjunto da agenda, não uma coleção adicional para somar. Ao mostrar uma seção “Próximos dias”, filtrar `agendaSemanal` por `dataOcorrencia !== dataReferencia`.
- Uma viagem semanal tem uma ocorrência nessa janela. Não gerar novas viagens no navegador.
- `dataOcorrencia` vem calculada pelo backend a partir do dia da grade.
- Não usar `Viagem.data`, o campo `data` dos DTOs antigos, nem a data de cadastro. Uma data antiga ou futura não muda a programação.
- `programacaoSemGrade` contém grade ausente ou sem dia. Mostrar “Programação não definida”; nunca usar a data legada como alternativa.
- O enum atual só aceita segunda a sexta. Não oferecer sábado/domingo sem uma futura alteração do contrato.
- Sem horários no modelo, não mostrar “próxima saída”, “saindo em 10 minutos” ou “viagem concluída”.
- Estado não OPERANDO ou não informado deixa `onibusDisponivel=false`. Exibir alerta e manter a viagem visível. Isso não é cancelamento.
- Não mostrar mapa, GPS, tempo de deslocamento ou um ônibus exclusivo do aluno.

O componente compartilhado `AgendaTransporte` pode receber `transporte` e `dataReferencia` por inputs obrigatórios e atender aluno/motorista. Renderizar cada viagem com:

1. Dia/data calculada e identificação da viagem/grade.
2. Ônibus: número, placa, modelo, capacidade, estado; sem imagem/base64.
3. Motorista: nome, sem CNH/telefone/salário.
4. Rota: nome, descrição e lista numerada dos pontos.
5. Todas as faculdades atendidas, não somente a faculdade principal da rota.
6. Todos os avisos retornados; null significa “Não informado”, não erro de template.

Usar `track viagem.id`, `track ponto.id` e `track faculdade.id`. Pontos já vêm deduplicados e ordenados por ordemParada (sem ordem ao final); não fazer outra busca nem reordenar alfabeticamente.

## 7. Layout do aluno: transporte e cobrança à primeira vista

Em tela grande: cabeçalho com faculdade, depois duas colunas — transporte maior à esquerda e cobrança à direita. Em celular: faculdade, transporte de hoje, cobrança, próximos dias, rotas/pontos. Não esconder cobrança ou identificação do ônibus apenas dentro de abas.

Aplicar grid responsivo, por exemplo:

```css
.destaques { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); gap: 1rem; }
.card { min-width: 0; overflow-wrap: anywhere; padding: 1rem; border-radius: 12px; }
@media (max-width: 768px) { .destaques { grid-template-columns: 1fr; } }
```

### Transporte

- Faculdade: nome/endereço, matrícula e situação resumida.
- Hoje: todas as viagens, todos os ônibus relacionados e itinerários. Se nenhuma hoje, informar isso e mostrar imediatamente os próximos dias.
- Próximos dias: tabela/cartões com data, dia, ônibus, rota e destinos.
- Programação não definida: bloco separado com aviso para revisão administrativa.
- Rotas e pontos: `transporte.rotas`, agrupados por rota. Pode haver rota da faculdade sem viagem; indicar “Rota cadastrada”, não inventar viagem.
- Transporte é relacionado à faculdade pelos destinos explícitos de viagem, não uma reserva/designação individual. Usar título “Transporte da sua faculdade”; não “Seu ônibus exclusivo” ou “Seu ponto”.
- Faculdade nula: aviso do backend e área de transporte vazia; financeiro continua disponível.

### Cobrança

Mostrar separadamente:

- “Mensalidade do cadastro”: `financeiro.mensalidadeCadastro`.
- “Cobrança em destaque”: `cobrancaPrincipal.valor`, descrição, vencimento e situação calculada.
- Contas em aberto e vencidas: quantidades e valores retornados.
- “Ver todas as cobranças”, link para `/minhas-matriculas`.

O backend escolhe a vencida mais antiga; se nenhuma, a aberta com vencimento mais próximo; se todas pagas, a paga com vencimento mais recente. O frontend não escolhe novamente. Conta sem vencimento fica depois das abertas datadas.

Rótulos sugeridos:

| Situação calculada | Texto/estilo |
|---|---|
| VENCIDA | “Vencida há N dias”, vermelho; N = absoluto de diasParaVencimento |
| VENCE_HOJE | “Vence hoje”, destaque de atenção |
| A_VENCER | “Vence em N dia(s)” |
| PAGO | “Pago”, verde, sem contador negativo |
| SEM_VENCIMENTO | “Vencimento não informado”, alerta, sem contador |
| cobrança null | “Nenhuma cobrança cadastrada”, sem botão pagar/recibo |

Usar `situacao` para o destaque, não o status persistido. Uma conta PENDENTE pode estar vencida; uma conta paga nunca deve aparecer atrasada. `diasParaVencimento` é null para contas pagas ou sem data; não converter null para zero.

Não usar “Sua matrícula expira em...” ou bloquear acesso após o vencimento. Vencimento é da cobrança. A leitura não altera o status de matrícula.

## 8. Pagamento e recibo compartilhados, sem duplicar regras

Já existe `RecebimentoService.pagar(contaId, formaPagamento)`, que envia:

```http
POST /recebimentos/realizar/42?formaPagamento=PIX
Authorization: Bearer <token>
```

Não enviar valor ou identidade. O backend valida propriedade e paga o valor integral persistido. A resposta é `ReciboMatricula`. Para consultar recibo já emitido, usar `buscarRecibo(contaId)`, GET `/recebimentos/recibo/conta/42`.

Esta operação é um registro financeiro interno, não uma cobrança real em banco/operadora. Não mostrar QR PIX, boleto bancário ou captura de cartão fictícios.

### Refatoração recomendada no frontend

Extrair da tela `MinhasMatriculas` um componente standalone `PagamentoMatricula` para **os dois** lugares. Não copiar os métodos de pagamento para o dashboard. O componente deve concentrar diálogo, forma, carregamento, confirmação, erro e visualização do recibo.

Aceitar um contrato mínimo, compatível com a cobrança completa e o resumo do dashboard:

```ts
export interface CobrancaParaPagamento {
  id: number;
  descricao: string | null;
  valor: number | null;
  dataVencimento: string | null;
  status: 'PENDENTE' | 'PAGO' | 'ATRASADO' | null;
}
```

Não fazer cast de `CobrancaResumo` para a entidade completa de conta. Usar esse contrato compartilhado como input. Recomenda-se:

- `conta = input<CobrancaParaPagamento | null>(null)`;
- `concluido = output<ReciboMatricula>()`;
- `reconsultar = output<void>()`, para conflito/registro removido;
- signal `pagando`, erro do diálogo e recibo;
- select com PIX, DINHEIRO, CARTAO, BOLETO e TRANSFERENCIA;
- botão “Pagar” somente se conta existe e status não é PAGO; se valor/data ausentes, mostrar inconsistência para revisão em vez de um valor inventado;
- botão “Ver recibo” para conta PAGO; consultar backend, não construir recibo local.

Núcleo da confirmação dentro desse componente, reutilizando o serviço existente:

```ts
confirmarPagamento(): void {
  const conta = this.conta();
  if (!conta || conta.status === 'PAGO' || this.pagando()) return;
  this.pagando.set(true);
  this.erroPagamento.set(null);
  this.recebimentoService.pagar(conta.id, this.formaPagamento).pipe(
    takeUntilDestroyed(this.destroyRef),
    finalize(() => this.pagando.set(false)),
  ).subscribe({
    next: recibo => {
      this.recibo.set(recibo);
      this.dialogoAberto.set(false);
      this.concluido.emit(recibo);
    },
    error: (error: HttpErrorResponse) => {
      this.erroPagamento.set(mensagemApi(error));
      if (error.status === 409 || error.status === 404) {
        this.dialogoAberto.set(false);
        this.reconsultar.emit();
      }
    },
  });
}
```

Esse trecho pressupõe os inputs/outputs/signals acima e `destroyRef = inject(DestroyRef)`, `recebimentoService = inject(RecebimentoService)`. Os imports de signals/input/output são de @angular/core, takeUntilDestroyed de @angular/core/rxjs-interop e finalize de rxjs. Migrar também os métodos existentes de abrir/fechar diálogo e consultar/fechar recibo; manter um único código de pagamento.

Bloquear confirmação duplicada e fechamento enquanto pagando. Mostrar erros do POST no diálogo. Não fazer retry automático de POST: se a conexão cair depois da gravação, reconsultar a conta e o recibo antes de tentar novamente.

Uso no HTML do aluno, após importar o componente em DashboardAluno:

```html
@if (dados(); as painel) {
  <app-pagamento-matricula
    [conta]="painel.financeiro.cobrancaPrincipal"
    (concluido)="carregar()"
    (reconsultar)="carregar()" />
}
```

Na tela MinhasMatriculas, usar o mesmo componente para a conta selecionada e ligar os eventos a `carregar()`. Remover os métodos/diálogos duplicados da tela original depois de migrá-los.

Após sucesso, consultar novamente GET /dashboard/aluno. O backend pode mudar cobrança em destaque, totais e status de matrícula; não somente trocar a etiqueta da conta local para PAGO. Se a consulta falhar depois de pagar, informar “Pagamento registrado; não foi possível atualizar o painel” e oferecer atualização. Isso não significa falha do pagamento.

## 9. Layout do motorista

Cabeçalho “Sua operação”, com nome e referência. Ordem:

1. Todas as viagens de hoje, ônibus/placa/estado, motorista, rota, pontos e destinos.
2. Próximas programações nos seis dias seguintes.
3. Programação não definida, com aviso administrativo.
4. Atalhos para `/minhas-viagens`, `/meus-chamados` e `/perfil`.

Reutilizar AgendaTransporte. Não consultar o endpoint administrativo de ônibus para complementar: os resumos já contêm o necessário. Não oferecer cadastros ou financeiro administrativo. A janela semanal não se baseia em data legada, mesmo que a tela antiga de viagens ainda exponha esse campo.

## 10. Layout administrativo e seletor mensal

Cabeçalho: Início, seletor `input type="month"`, referência/última atualização e botão Atualizar.

Primeira linha:

- Recebido no mês: recebimentos efetivos.
- Pago no mês: pagamentos efetivos.
- Saldo das movimentações: recebido menos pago; pode ser negativo.

Não chamar o terceiro cartão de “Saldo bancário” ou “Lucro”.

Segunda linha, sempre identificada como **posição atual de todos os períodos**, independentemente do mês selecionado:

- A receber e a pagar em aberto.
- A receber e a pagar vencidos.
- Vencimentos de hoje até a referência + seis dias (inclusive), para cada tipo.

Os vencidos fazem parte dos abertos: não somar esses cartões entre si. Mês selecionado afeta os três cartões de movimento e a série; não filtra pendências, matrícula, estado de ônibus ou chamados.

### Carregar e trocar mês sem respostas fora de ordem

No componente administrativo, usar Subject + switchMap para cancelar consulta anterior se o usuário trocar rapidamente o mês:

```ts
// Imports adicionais: Subject, switchMap, catchError, EMPTY, tap, finalize.
private readonly consultas = new Subject<string | undefined>();
readonly mesSelecionado = signal<string | undefined>(undefined);
readonly dados = signal<AdminDashboard | null>(null);
readonly erro = signal<string | null>(null);
readonly carregando = signal(false);

ngOnInit(): void {
  this.consultas.pipe(
    switchMap(mes => {
      this.carregando.set(true);
      this.erro.set(null);
      return this.service.admin(mes).pipe(
        tap(painel => {
          this.dados.set(painel);
          this.mesSelecionado.set(painel.mes);
        }),
        catchError((error: HttpErrorResponse) => {
          this.erro.set(mensagemApi(error));
          return EMPTY;
        }),
        finalize(() => this.carregando.set(false)),
      );
    }),
    takeUntilDestroyed(this.destroyRef),
  ).subscribe();
  this.consultas.next(undefined); // Backend decide mês atual em São Paulo.
}
alterarMes(valor: string): void {
  if (!/^[0-9]{4}-(0[1-9]|1[0-2])$/.test(valor) || valor.startsWith('0000-')) {
    this.erro.set('Selecione um mês válido.');
    return;
  }
  this.mesSelecionado.set(valor);
  this.consultas.next(valor);
}
carregar(): void { this.consultas.next(this.mesSelecionado()); }
```

Usar o mesmo padrão de injeções/imports do aluno e substituir o template. O catchError fica **dentro** de switchMap, para uma falha não encerrar futuras atualizações.

```html
<label for="mes-painel">Mês das movimentações</label>
<input id="mes-painel" type="month" [value]="mesSelecionado() ?? ''"
       (change)="alterarMes($any($event.target).value)" />
```

Não marcar os cartões antigos com o novo mês enquanto a consulta estiver pendente: renderizar skeleton/erro e mostrar o mês da resposta válida.

### Gráfico de seis meses

Usar ChartModule de PrimeNG com chart.js instalado/configurado. Não calcular novos meses ou somas; o backend já envia seis posições, incluindo meses zerados:

```ts
readonly grafico = computed(() => {
  const serie = this.dados()?.financeiro.serieMensal ?? [];
  return {
    labels: serie.map(m => m.mes),
    datasets: [
      { label: 'Recebido', data: serie.map(m => m.recebido), backgroundColor: '#16a34a' },
      { label: 'Pago', data: serie.map(m => m.pago), backgroundColor: '#dc2626' },
    ],
  };
});
```

```html
<p-chart type="bar" [data]="grafico()" />
```

Importar computed de @angular/core e ChartModule de primeng/chart. Formatador de tooltip/eixo usa BRL. Exibir alternativa em tabela para acessibilidade e para conferir os valores; não incluir salário nominal de motorista como movimentação.

### Operação e pendências acionáveis

- Alunos: total, ativos, inativos, pendentes e sem situação. Atenção aos valores ATIV0/INATIV0 do cadastro.
- Ônibus: total e cada estado, inclusive “Não informado”.
- Viagens de hoje e viagens sem programação.
- Chamados: abertos, em andamento e prioridade alta pendente (alta não inclui resolvidos).
- Cinco contas abertas mais antigas a receber e cinco a pagar: nome, descrição, valor, vencimento e situação.
- Cinco chamados pendentes: ônibus, solicitante, relato, urgência, andamento e data, na ordem fornecida.

Links atuais: receber `/contas-receber`, pagar `/contapagar`, chamados `/manutencao`, grades `/grades`, veículos `/onibus`, alunos `/alunos`.

As telas atuais não necessariamente leem IDs por queryParam. Para abrir uma pendência específica, implementar nelas um parâmetro validado, por exemplo `?contaId=42` ou `?chamadoId=12`, selecionando o registro somente depois de a própria tela consultar o backend autorizado. Enquanto isso não existir, usar link para a listagem, sem prometer que o item abrirá automaticamente. Não enviar pagamento pelo dashboard ADMIN usando o endpoint de aluno.

## 11. Datas, dinheiro e acessibilidade

- Configurar locale pt-BR no app e usar CurrencyPipe: `valor | currency:'BRL':'symbol':'1.2-2':'pt-BR'`.
- Dados monetários null: “Não informado”, não R$ 0,00. Totais zero realmente retornados: R$ 0,00.
- LocalDate é data sem horário. Usar `data | date:'dd/MM/yyyy':'UTC'` para evitar deslocamento, ou separar ano/mês/dia como texto. Não usar `new Date('2026-10-12').toLocaleDateString()` sem fuso.
- `geradoEm` é instante com offset: formatar com Intl.DateTimeFormat('pt-BR', { timeZone: 'America/Sao_Paulo', dateStyle: 'short', timeStyle: 'short' }) se precisar do horário.
- Comparar datas da agenda pelas strings ISO já recebidas. Contadores de cobrança vêm do servidor, não do relógio do computador.
- Botão Atualizar em todos os papéis; atualização automática depois do pagamento e ao entrar na página. Não é necessário polling.
- Usar alertas com texto e ícone, não só cor; rótulos acessíveis nos seletores/botões, contraste e largura adequada no celular.
- Interpolação Angular para descrições/avisos; não injetar HTML fornecido por usuários.
- Guards/menu são conveniência de navegação. Autorização real continua nas URLs e nos serviços do backend.

## 12. Exemplos JSON

Exemplo de aluno, referência segunda-feira; todas as datas de ocorrência são calculadas pela grade:

```json
{
  "dataReferencia": "2026-10-12",
  "geradoEm": "2026-10-12T12:00:00-03:00",
  "aluno": { "pessoaId": 2, "nome": "Ana", "matricula": "2026001", "statusMatricula": "PENDENTE" },
  "faculdade": { "id": 1, "nome": "Faculdade A", "endereco": "Rua A" },
  "transporte": {
    "viagensHoje": [{
      "id": 10,
      "grade": { "id": 1, "diaSemana": "SEGUNDA", "descricao": "Grade de segunda" },
      "dataOcorrencia": "2026-10-12",
      "rota": { "id": 1, "nome": "Centro", "descricao": "Centro até os campi", "pontosEmbarque": [{ "id": 1, "descricao": "Praça", "ordemParada": 1 }] },
      "onibus": { "id": 1, "numeroIdentificacao": "01", "placa": "ABC1234", "modelo": "Modelo A", "capacidade": 40, "statusOnibus": "OPERANDO" },
      "motorista": { "pessoaId": 3, "nome": "João" },
      "faculdades": [{ "id": 1, "nome": "Faculdade A", "endereco": "Rua A" }],
      "onibusDisponivel": true,
      "avisos": []
    }],
    "agendaSemanal": [{
      "id": 10,
      "grade": { "id": 1, "diaSemana": "SEGUNDA", "descricao": "Grade de segunda" },
      "dataOcorrencia": "2026-10-12",
      "rota": { "id": 1, "nome": "Centro", "descricao": "Centro até os campi", "pontosEmbarque": [{ "id": 1, "descricao": "Praça", "ordemParada": 1 }] },
      "onibus": { "id": 1, "numeroIdentificacao": "01", "placa": "ABC1234", "modelo": "Modelo A", "capacidade": 40, "statusOnibus": "OPERANDO" },
      "motorista": { "pessoaId": 3, "nome": "João" },
      "faculdades": [{ "id": 1, "nome": "Faculdade A", "endereco": "Rua A" }],
      "onibusDisponivel": true,
      "avisos": []
    }],
    "programacaoSemGrade": [],
    "rotas": [{ "id": 1, "nome": "Centro", "descricao": "Centro até os campi", "pontosEmbarque": [{ "id": 1, "descricao": "Praça", "ordemParada": 1 }] }]
  },
  "financeiro": {
    "mensalidadeCadastro": 250.00,
    "cobrancaPrincipal": { "id": 42, "descricao": "Matrícula - Ana", "valor": 230.00, "dataVencimento": "2026-10-12", "status": "PENDENTE", "situacao": "VENCE_HOJE", "diasParaVencimento": 0 },
    "emAberto": { "quantidade": 1, "valor": 230.00 },
    "vencidas": { "quantidade": 0, "valor": 0 }
  },
  "avisos": []
}
```

Exemplo de motorista sem atribuições:

```json
{
  "dataReferencia": "2026-10-12",
  "geradoEm": "2026-10-12T12:00:00-03:00",
  "motorista": { "pessoaId": 3, "nome": "João" },
  "transporte": { "viagensHoje": [], "agendaSemanal": [], "programacaoSemGrade": [], "rotas": [] },
  "avisos": ["Nenhuma viagem com programação semanal foi atribuída a você."]
}
```

Exemplo administrativo de estrutura completa, com movimentações zero:

```json
{
  "dataReferencia": "2026-10-12",
  "geradoEm": "2026-10-12T12:00:00-03:00",
  "mes": "2026-10",
  "financeiro": {
    "recebidoNoMes": 0,
    "pagoNoMes": 0,
    "saldoMovimentacoes": 0,
    "contasReceber": { "emAberto": { "quantidade": 0, "valor": 0 }, "vencidas": { "quantidade": 0, "valor": 0 }, "vencemEmSeteDias": { "quantidade": 0, "valor": 0 } },
    "contasPagar": { "emAberto": { "quantidade": 0, "valor": 0 }, "vencidas": { "quantidade": 0, "valor": 0 }, "vencemEmSeteDias": { "quantidade": 0, "valor": 0 } },
    "serieMensal": [
      { "mes": "2026-05", "recebido": 0, "pago": 0, "saldoMovimentacoes": 0 },
      { "mes": "2026-06", "recebido": 0, "pago": 0, "saldoMovimentacoes": 0 },
      { "mes": "2026-07", "recebido": 0, "pago": 0, "saldoMovimentacoes": 0 },
      { "mes": "2026-08", "recebido": 0, "pago": 0, "saldoMovimentacoes": 0 },
      { "mes": "2026-09", "recebido": 0, "pago": 0, "saldoMovimentacoes": 0 },
      { "mes": "2026-10", "recebido": 0, "pago": 0, "saldoMovimentacoes": 0 }
    ],
    "pendenciasReceber": [],
    "pendenciasPagar": []
  },
  "operacao": {
    "alunos": { "total": 0, "ativos": 0, "inativos": 0, "pendentes": 0, "semStatus": 0 },
    "onibus": { "total": 0, "operando": 0, "manutencao": 0, "quebrado": 0, "inativo": 0, "semStatus": 0 },
    "viagensHoje": 0,
    "viagensSemProgramacao": 0,
    "chamados": { "abertos": 0, "emAndamento": 0, "prioridadeAlta": 0 },
    "chamadosPendentes": []
  }
}
```

## 13. Erros e estados vazios

| Resposta | Comportamento |
|---|---|
| 200 com arrays vazios | Mostrar estado vazio específico e avisos |
| 400 | Mês inválido ou dado inconsistente; mostrar detail e permitir correção |
| 401 | Interceptor encerra sessão e volta ao login; não mostrar “Sem viagens” |
| 403 | Sem permissão; usar tela existente, sem tentar outro endpoint como alternativa |
| 404 em pagamento/recibo | Cobrança indisponível; atualizar painel, não inventar recibo |
| 409 em pagamento | Reconsultar conta/recibo; não enviar novamente automaticamente |
| status 0 / 5xx | Backend indisponível/erro; mensagem e botão tentar de novo, nunca valores zero fictícios |

O dashboard não possui GET por ID e não retorna 404 por faculdade ausente: esse caso é resposta 200 com aviso. Problemas de autorização nunca equivalem a ausência de registros.

## 14. Checklist de implementação e testes Angular

Ordem sugerida: contratos/serviço → rota/distribuidor/menu/login → carregamento → aluno/motorista → financeiro administrativo → pagamento compartilhado → responsividade/testes.

- [ ] Login normal de cada papel abre /inicio; retorno seguro válido continua funcionando.
- [ ] /perfil e todas as telas existentes continuam acessíveis conforme o papel.
- [ ] Apenas um endpoint do dashboard é consultado ao abrir a página.
- [ ] ALUNO não recebe viagens apenas pela faculdade principal da rota; múltiplos destinos funcionam.
- [ ] Rota cadastrada sem viagem aparece nos pontos/rotas, sem viagem fictícia.
- [ ] Pontos duplicados nos vínculos da rota aparecem uma vez, na ordem do servidor.
- [ ] Faculdade ausente é aviso, não falha; financeiro continua disponível.
- [ ] MOTORISTA não vê viagens de outro motorista e não recebe atalhos administrativos.
- [ ] Grade/dia determina agenda; datas antigas, futuras ou null não interferem.
- [ ] Todas as viagens do dia aparecem; hoje não é contado duas vezes.
- [ ] Viagem sem grade fica em programação não definida, sem usar data antiga.
- [ ] Ônibus indisponível permanece visível com alerta, sem “cancelada” automática.
- [ ] Cobrança vencida, hoje, futura, paga e ausente têm textos corretos.
- [ ] Mensalidade cadastrada diferente da cobrança não muda o valor pago.
- [ ] Pagamento/recibo usam componente e serviço únicos nas duas telas.
- [ ] Botão de confirmação não duplica POST; sucesso refaz GET do dashboard.
- [ ] Vencimento não bloqueia matrícula/acesso automaticamente.
- [ ] Mês selecionado afeta movimentações/série; pendências continuam posição atual.
- [ ] Gráfico possui seis meses; saldo negativo aparece; nenhuma soma duplicada.
- [ ] Botões das pendências levam às telas existentes; filtros por ID só se implementados.
- [ ] Falha de GET nunca vira “sem dados” ou totais zero.
- [ ] Tela legível em 360px e desktop, sem foto grande ou CPF/CNH/salário.

Testes sugeridos com o runner já configurado no Angular:

- DashboardService com HttpTestingController: URLs, parâmetro mes e ausência de IDs extras. Registrar provideHttpClient() antes de provideHttpClientTesting().
- Inicio: simular cada papel e verificar criação somente do respectivo componente.
- Loader: sucesso vazio, falha 0/403/500 e atualização sem exibir dados antigos como atuais.
- Aluno: fixtures para cada situação de cobrança, faculdade null e rotas sem viagens.
- Agenda: várias viagens hoje, agenda sem duplicar hoje, programação sem grade e ônibus indisponível.
- Pagamento compartilhado: dupla confirmação bloqueada, POST sem valor/identidade, eventos após sucesso e reconsulta em 404/409.
- Admin: mês inválido não consulta, troca rápida cancela consulta anterior, gráfico respeita a série recebida.
- Datas: executar com fuso do navegador diferente; LocalDate continua no mesmo dia.

Antes da entrega do frontend, executar os testes e o build Angular. Este guia não substitui essa validação e não afirma que a interface já foi implementada.
