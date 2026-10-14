# Programação diária por e-mail — guia de implementação no frontend

Referência: 14/10/2026. Backend na branch `codex/integracao-email`, criada a partir da `main`.
Este guia orienta a implementação no Angular 21 standalone/PrimeNG. Nenhum código Angular foi alterado nesta entrega.

## 1. O que mudou em relação ao WhatsApp

- Não há integração com Meta, confirmação por WhatsApp, telefone ou PDF.
- Contas ativas de alunos não arquivados e de motoristas recebem a mesma programação geral do dia.
- O endereço vem de `Usuario.email`, que também é utilizado no login. Pessoas sem conta não possuem e-mail no modelo atual.
- ADMIN não é destinatário. Não há filtro por faculdade nem por situação da matrícula.
- Arquivamento do aluno impede seleção e envio, mesmo com acesso indevidamente ativo. Restaurar não reativa a conta nem reenfileira mensagens suprimidas.
- Cada mensagem é enviada separadamente: um destinatário não vê os e-mails dos demais.
- O corpo tem HTML e texto simples, agrupados por faculdade e destacando número do ônibus e motorista.
- Uma viagem com múltiplos destinos aparece em cada seção correspondente.
- Todas as grades do dia são consideradas. `Viagem.data` antiga é ignorada.
- Não há horário de saída cadastrado, previsão de chegada ou atribuição individual de aluno–ônibus.
- Ônibus indisponível permanece na escala com aviso, sem cancelamento automático.
- Sem viagens do dia, nenhum e-mail é enviado. O frontend deve mostrar isso explicitamente.
- Envio manual e controle do automático são exclusivos de ADMIN.
- Automático às 15h em `America/Sao_Paulo`, recuperável após reinício até antes das 18h.
- Manual anterior evita repetição automática para o mesmo endereço naquele dia. Correções exigem reenvio confirmado e motivo.

Não alterar as telas atuais de aluno/motorista para abrir essa administração. Eles recebem o e-mail e continuam usando o dashboard normal.

## 2. Preparar o backend antes de implementar a tela

A integração e o automático começam desativados. É possível consultar configuração, prévia e histórico sem SMTP funcional.
Enviar ou habilitar o automático requer configuração válida.

O banco atual já aplicou a V6 do WhatsApp. Por isso, seu SQL original permanece apenas para compatibilidade Flyway; a funcionalidade nova está em `V7__programacao_email.sql`.
Não apagar tabelas antigas, alterar checksums, executar `flyway repair` ou editar o histórico manualmente.
Não existem classes, endpoints, credenciais ou rotinas de WhatsApp nessa branch.
Os dados antigos são preservados; as tabelas antigas ficam sem uso. Em bancos novos, V1–V8 são aplicadas: V7 implementa e-mail e V8 acrescenta arquivamento de alunos.

Antes de trocar branches em um ambiente importante, fazer backup. Reiniciar o backend usando a nova branch, para não continuar executando o processo antigo.

## 3. Endpoints reais

Todos exigem JWT de ADMIN. O interceptor existente deve adicionar Bearer normalmente.

| Método e caminho | Finalidade |
|---|---|
| GET `/integracoes/email/configuracao` | disponibilidade, impedimentos, modo de teste, horário e automático |
| PUT `/integracoes/email/configuracao` | ativar/desativar automático |
| GET `/integracoes/email/previa` | viagens gerais de hoje, destinatários elegíveis e mensagem gerada |
| POST `/integracoes/email/envios` | enfileirar envio manual; retorna 202 |
| GET `/integracoes/email/envios?pagina=0&tamanho=20` | histórico paginado; tamanho máximo 100 |
| GET `/integracoes/email/envios/{id}` | entregas e conteúdo congelado do lote |

Não enviar lista de e-mails, faculdadeId, usuário destinatário, data, assunto ou HTML no POST.
O backend escolhe os associados e gera o conteúdo. Somente a programação de hoje é enviada.

Ativar:

```json
{ "automatico": true }
```

Primeiro envio do dia:

```json
{
  "requestId": "9bfc26ac-201a-43e6-b814-95c76ebcb661",
  "reenviar": false
}
```

Correção explícita:

```json
{
  "requestId": "de1f3a93-5927-4208-a6e4-9b888846ca33",
  "reenviar": true,
  "motivo": "Ônibus da Faculdade São José alterado de 07 para 09."
}
```

`requestId` é UUID criado com `crypto.randomUUID()`. Repetir a mesma solicitação usa o MESMO UUID e os mesmos parâmetros.
Outro UUID representa outra ação. Mesmo UUID com parâmetros diferentes retorna 409.
Não usar `retry()` indiscriminadamente em operações de envio, especialmente no reenvio.

## 4. Organização sugerida

Seguir a organização registrada no guia do dashboard:

```text
src/app/components/email-programacao/
  models/email-programacao.models.ts
  service/email-programacao-service.ts
  components/email-programacao/email-programacao.ts
  components/email-programacao/email-programacao.html
  components/email-programacao/email-programacao.scss
```

Reutilizar:

- `environment.apiUrl`;
- AuthService de `src/app/core/auth/service/auth.service.ts`;
- guards de `src/app/core/auth/guards/auth.guards.ts`;
- interceptor JWT existente;
- estilos e feedback visual existentes;
- tratamento central de 401, sem criar um segundo interceptor.

Os caminhos acima vêm da organização descrita nos guias anteriores; conferir os nomes reais no checkout do frontend antes de copiar os imports.

## 5. Interfaces TypeScript

`models/email-programacao.models.ts`:

```ts
export type EstadoEntrega =
  | 'PENDENTE' | 'PROCESSANDO' | 'ACEITA_SMTP'
  | 'FALHA' | 'INCERTA' | 'SUPRIMIDA' | 'EXPIRADA';

export interface ConteudoEmail {
  assunto: string;
  texto: string;
  html: string;
}

export interface ConfiguracaoEmail {
  habilitado: boolean;
  automatico: boolean;
  modoTeste: boolean;
  disponivel: boolean;
  horario: string;
  fuso: string;
  impedimentos: string[];
}

export interface ViagemProgramacao {
  id: number;
  grade: {
    id: number;
    diaSemana: 'SEGUNDA' | 'TERCA' | 'QUARTA' | 'QUINTA' | 'SEXTA';
    descricao: string | null;
  } | null;
  dataOcorrencia: string;
  rota: {
    id: number;
    nome: string | null;
    descricao: string | null;
    pontosEmbarque: {
      id: number; descricao: string | null; ordemParada: number | null;
    }[];
  } | null;
  onibus: {
    id: number;
    numeroIdentificacao: string | null;
    placa: string | null;
    modelo: string | null;
    capacidade: number | null;
    statusOnibus: 'OPERANDO' | 'MANUTENCAO' | 'QUEBRADO' | 'INATIVO' | null;
  } | null;
  motorista: { pessoaId: number; nome: string | null } | null;
  faculdades: { id: number; nome: string | null; endereco: string | null }[];
  onibusDisponivel: boolean;
  avisos: string[];
}

export interface PreviaEmail {
  dataReferencia: string; // yyyy-MM-dd; não converter diretamente para UTC.
  geradoEm: string;
  destinatariosElegiveis: number; // já considera modo de teste e deduplicação.
  destinatariosIgnorados: number;
  viagens: ViagemProgramacao[];
  avisos: string[];
  mensagem: ConteudoEmail;
}

export interface EnvioEmailRequest {
  requestId: string;
  reenviar: boolean;
  motivo?: string;
}

export interface EntregaEmail {
  id: string;
  usuarioId: number; // identificação histórica; a conta pode já ter sido excluída.
  email: string;
  revisao: number; // começa em 0.
  estado: EstadoEntrega;
  tentativas: number;
  erro: string | null; // código seguro, não erro SMTP bruto.
  aceitoEm: string | null;
}

export interface LoteEmail {
  id: string;
  dataReferencia: string;
  origem: 'MANUAL' | 'AUTOMATICA';
  reenvio: boolean;
  motivo: string | null;
  criadoEm: string;
  ignorados: number;
  totais: Record<EstadoEntrega, number>;
  entregas: EntregaEmail[];
  mensagem: ConteudoEmail | null;
  // Histórico: entregas=[] e mensagem=null.
  // Detalhe/POST: entregas preenchidas e mensagem congelada.
}

export interface HistoricoEmail {
  pagina: number;
  tamanho: number;
  totalElementos: number;
  totalPaginas: number;
  itens: LoteEmail[];
}
```

Se `ViagemResumo` já estiver implementado no dashboard, reutilizar esse contrato em vez de manter duas interfaces equivalentes.

Exemplo de configuração inicial:

```json
{
  "habilitado": false,
  "automatico": false,
  "modoTeste": true,
  "disponivel": false,
  "horario": "15:00",
  "fuso": "America/Sao_Paulo",
  "impedimentos": [
    "Integração desativada no servidor.",
    "Servidor SMTP não configurado.",
    "Remetente não configurado ou inválido.",
    "Modo de teste sem destinatários permitidos."
  ]
}
```

## 6. Serviço HTTP

`service/email-programacao-service.ts`:

```ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import {
  ConfiguracaoEmail, PreviaEmail, EnvioEmailRequest, LoteEmail, HistoricoEmail
} from '../models/email-programacao.models';

@Injectable({ providedIn: 'root' })
export class EmailProgramacaoService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl.replace(/\/$/, '')}/integracoes/email`;

  configuracao() {
    return this.http.get<ConfiguracaoEmail>(`${this.base}/configuracao`);
  }

  configurar(automatico: boolean) {
    return this.http.put<ConfiguracaoEmail>(
      `${this.base}/configuracao`, { automatico }
    );
  }

  previa() {
    return this.http.get<PreviaEmail>(`${this.base}/previa`);
  }

  enviar(request: EnvioEmailRequest) {
    return this.http.post<LoteEmail>(`${this.base}/envios`, request);
  }

  historico(pagina = 0, tamanho = 20) {
    return this.http.get<HistoricoEmail>(`${this.base}/envios`, {
      params: { pagina, tamanho }
    });
  }

  detalhe(id: string) {
    return this.http.get<LoteEmail>(`${this.base}/envios/${encodeURIComponent(id)}`);
  }
}
```

Não colocar credenciais SMTP no environment do Angular: o bundle é público.
O serviço não precisa montar manualmente o header de autenticação.

## 7. Rota e menu

Em `app.routes.ts`, antes do curinga:

```ts
{
  path: 'programacao-email',
  loadComponent: () =>
    import('./components/email-programacao/components/email-programacao/email-programacao')
      .then(m => m.EmailProgramacao),
  canActivate: [authGuard, roleGuard(['ADMIN'])],
}
```

No menu administrativo:

```ts
{
  label: 'Programação por e-mail',
  icon: 'pi pi-envelope',
  rota: '/programacao-email'
}
```

Preservar Início, Perfil, financeiro e cadastros existentes. Não colocar esse item nos menus ALUNO/MOTORISTA.
Guards e menus não substituem a autorização do backend.

## 8. Tela administrativa

Ordem recomendada:

1. Título e descrição: "Programação geral de hoje para alunos e motoristas".
2. Estado da configuração, modo de teste, automático e horário/fuso.
3. Impedimentos visíveis, com orientações de configuração no servidor.
4. Prévia: data, quantidade de viagens, destinatários elegíveis/ignorados.
5. Tabela com faculdades, número do ônibus, motorista, rota e avisos.
6. Texto do e-mail ou cartões equivalentes à mensagem; nunca um editor livre.
7. Ações "Enviar programação de hoje" e "Reenviar correção".
8. Histórico paginado e detalhe do lote.

A quantidade da prévia não desconta necessariamente os destinatários que já receberam hoje. O lote calcula essa duplicidade e informa `ignorados`.
Não prometer que todos serão enfileirados apenas pela prévia.

Desabilitar envio quando:

- carregamento/envio em andamento;
- configuração indisponível;
- sem viagens;
- zero destinatários elegíveis;
- solicitação anterior ainda aguardando resultado.

Permitir desativar automático mesmo que SMTP esteja indisponível. Não atualizar o toggle otimisticamente: aguardar a resposta do backend.

## 9. Componente standalone de referência

O exemplo utiliza componentes estáveis já presentes no PrimeNG. Adaptar estilos e mensagens ao padrão do projeto.
`email-programacao.ts`:

```ts
import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin, finalize } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { DialogModule } from 'primeng/dialog';
import { EmailProgramacaoService } from '../../service/email-programacao-service';
import {
  ConfiguracaoEmail, PreviaEmail, HistoricoEmail, LoteEmail,
  EnvioEmailRequest, EstadoEntrega
} from '../../models/email-programacao.models';
import { AuthService } from '../../../../core/auth/service/auth.service';

@Component({
  selector: 'app-email-programacao',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, ButtonModule, TableModule, DialogModule
  ],
  templateUrl: './email-programacao.html',
  styleUrl: './email-programacao.scss',
})
export class EmailProgramacao implements OnInit {
  private readonly api = inject(EmailProgramacaoService);
  private readonly auth = inject(AuthService);
  readonly config = signal<ConfiguracaoEmail | null>(null);
  readonly previa = signal<PreviaEmail | null>(null);
  readonly historico = signal<HistoricoEmail | null>(null);
  readonly detalhe = signal<LoteEmail | null>(null);
  readonly carregando = signal(false);
  readonly enviando = signal(false);
  readonly configurando = signal(false);
  readonly erro = signal('');
  readonly aviso = signal('');
  readonly pendente = signal<EnvioEmailRequest | null>(null);

  pagina = 0;
  tamanho = 20;
  dialogoReenvio = false;
  readonly motivo = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(500)]
  });

  readonly podeEnviar = computed(() =>
    !!this.config()?.disponivel &&
    !!this.previa()?.viagens.length &&
    (this.previa()?.destinatariosElegiveis ?? 0) > 0 &&
    !this.carregando() && !this.enviando() && !this.pendente()
  );

  private get chave(): string {
    return `integrador.email.request.${this.auth.usuario()?.id ?? 'sem-usuario'}`;
  }

  ngOnInit(): void {
    this.restaurarPendente();
    this.atualizar();
  }

  atualizar(): void {
    if (this.carregando()) return;
    this.carregando.set(true);
    this.erro.set('');
    forkJoin({
      config: this.api.configuracao(),
      previa: this.api.previa(),
      historico: this.api.historico(this.pagina, this.tamanho),
    }).pipe(finalize(() => this.carregando.set(false))).subscribe({
      next: r => {
        this.config.set(r.config);
        this.previa.set(r.previa);
        this.historico.set(r.historico);
      },
      error: e => this.erro.set(this.mensagemErro(e)),
    });
  }

  configurarAutomatico(): void {
    const config = this.config();
    if (!config || this.configurando()) return;
    if (!config.automatico && !config.disponivel) return;
    this.configurando.set(true);
    this.erro.set('');
    this.api.configurar(!config.automatico)
      .pipe(finalize(() => this.configurando.set(false)))
      .subscribe({
        next: c => this.config.set(c),
        error: e => this.erro.set(this.mensagemErro(e)),
      });
  }

  iniciarEnvio(): void {
    if (!this.podeEnviar()) return;
    if (!window.confirm('Enviar a programação geral de hoje para os associados ativos?')) return;
    this.registrarPendente({ requestId: crypto.randomUUID(), reenviar: false });
    this.enviarPendente();
  }

  confirmarReenvio(): void {
    this.motivo.markAsTouched();
    const motivo = this.motivo.value.trim();
    if (!this.podeEnviar() || this.motivo.invalid || !motivo) return;
    if (!window.confirm('Esta correção poderá enviar outra mensagem para quem já recebeu hoje. Confirmar?')) return;
    this.registrarPendente({ requestId: crypto.randomUUID(), reenviar: true, motivo });
    this.dialogoReenvio = false;
    this.enviarPendente();
  }

  enviarPendente(): void {
    const request = this.pendente();
    if (!request || this.enviando()) return;
    this.enviando.set(true);
    this.erro.set('');
    this.api.enviar(request).pipe(finalize(() => this.enviando.set(false))).subscribe({
      next: lote => {
        this.registrarPendente(null);
        this.detalhe.set(lote);
        this.aviso.set('Solicitação registrada. Os e-mails serão processados pela fila; isso ainda não comprova entrega.');
        this.pagina = 0;
        this.atualizar();
      },
      error: (e: HttpErrorResponse) => {
        // Em falha de rede/5xx, o lote pode ter sido criado. Preservar o mesmo UUID.
        if (e.status > 0 && e.status < 500 && e.status !== 408)
          this.registrarPendente(null);
        this.erro.set(this.mensagemErro(e));
      },
    });
  }

  paginar(evento: { first?: number; rows?: number }): void {
    this.tamanho = evento.rows ?? 20;
    this.pagina = Math.floor((evento.first ?? 0) / this.tamanho);
    this.atualizar();
  }

  abrirDetalhe(id: string): void {
    this.erro.set('');
    this.api.detalhe(id).subscribe({
      next: lote => this.detalhe.set(lote),
      error: e => this.erro.set(this.mensagemErro(e)),
    });
  }

  atualizarDetalhe(): void {
    const lote = this.detalhe();
    if (lote) this.abrirDetalhe(lote.id);
    this.atualizar();
  }

  dataCivil(valor: string): string {
    const [ano, mes, dia] = valor.split('-');
    return `${dia}/${mes}/${ano}`;
  }

  estado(estado: EstadoEntrega): string {
    const labels: Record<EstadoEntrega, string> = {
      PENDENTE: 'Na fila',
      PROCESSANDO: 'Processando',
      ACEITA_SMTP: 'Aceito pelo servidor de e-mail',
      FALHA: 'Falha',
      INCERTA: 'Resultado incerto — conferir antes de reenviar',
      SUPRIMIDA: 'Não enviado: cadastro alterado/inativo',
      EXPIRADA: 'Janela de envio encerrada',
    };
    return labels[estado];
  }

  private registrarPendente(request: EnvioEmailRequest | null): void {
    this.pendente.set(request);
    if (request) sessionStorage.setItem(this.chave, JSON.stringify(request));
    else sessionStorage.removeItem(this.chave);
  }

  private restaurarPendente(): void {
    try {
      const raw = sessionStorage.getItem(this.chave);
      if (!raw) return;
      const value = JSON.parse(raw) as EnvioEmailRequest;
      if (typeof value.requestId !== 'string' || typeof value.reenviar !== 'boolean')
        throw new Error('Solicitação inválida');
      this.pendente.set(value);
    } catch { sessionStorage.removeItem(this.chave); }
  }

  private mensagemErro(e: HttpErrorResponse): string {
    const detail = e.error?.detail;
    switch (e.status) {
      case 0: return 'Não foi possível confirmar a solicitação. Confira o histórico e repita a mesma solicitação.';
      case 400: return detail ?? 'Revise os dados informados.';
      case 401: return 'Sessão expirada. Entre novamente.';
      case 403: return 'Esta operação é exclusiva de administrador.';
      case 404: return 'Lote não encontrado.';
      case 409: return detail ?? 'Conflito: atualize a prévia e confira o histórico.';
      case 503: return detail ?? 'SMTP indisponível ou não configurado.';
      default: return 'Falha no servidor. Confira o histórico antes de solicitar outro envio.';
    }
  }
}
```

O import do AuthService pressupõe a pasta `core/auth/service` indicada pelo guia do dashboard; ajustar se a estrutura real mudou.
A chave de solicitação pendente é separada por administrador. Ela não guarda JWT ou credenciais.
Em caso de falha ambígua, o botão "Repetir mesma solicitação" usa o UUID preservado, inclusive após recarregar a página.
Não oferecer um botão que simplesmente abandone um reenvio incerto e gere outro UUID sem conferir o histórico.

Para dados já presentes que falharem ao atualizar, manter os dados com aviso visível de consulta desatualizada. Não substituir silenciosamente por lista vazia.
O exemplo usa erro global quando uma das consultas falha; se dividir em painéis, cada painel deve ter seu próprio carregamento/erro.

## 10. Template da tela

`email-programacao.html`:

```html
<section class="email-painel">
  <header>
    <h1>Programação por e-mail</h1>
    <p>Escala geral do dia para todos os alunos e motoristas com conta ativa.</p>
    <p-button label="Atualizar" icon="pi pi-refresh"
      [loading]="carregando()" [disabled]="enviando()" (onClick)="atualizar()" />
  </header>

  @if (erro()) { <p role="alert" class="erro">{{ erro() }}</p> }
  @if (aviso()) { <p role="status">{{ aviso() }}</p> }

  @if (config(); as c) {
    <article class="cartao">
      <h2>Configuração</h2>
      <p>{{ c.disponivel ? 'Configuração pronta para solicitar envio' : 'Integração indisponível' }}</p>
      <p>{{ c.modoTeste ? 'Modo de teste: somente lista permitida' : 'Modo de produção' }}</p>
      <p>Automático: {{ c.automatico ? 'Ativado' : 'Desativado' }} — {{ c.horario }} ({{ c.fuso }})</p>
      @for (item of c.impedimentos; track item) { <p class="alerta">{{ item }}</p> }
      <p-button
        [label]="c.automatico ? 'Desativar automático' : 'Ativar automático'"
        [loading]="configurando()"
        [disabled]="configurando() || (!c.automatico && !c.disponivel)"
        (onClick)="configurarAutomatico()" />
    </article>
  }

  @if (previa(); as p) {
    <article class="cartao">
      <h2>Prévia — {{ dataCivil(p.dataReferencia) }}</h2>
      <p>{{ p.viagens.length }} viagens · {{ p.destinatariosElegiveis }} destinatários elegíveis ·
        {{ p.destinatariosIgnorados }} ignorados na seleção</p>
      @for (item of p.avisos; track item) { <p class="alerta">{{ item }}</p> }

      <p-table [value]="p.viagens" [tableStyle]="{ 'min-width': '45rem' }">
        <ng-template #header>
          <tr><th>Faculdades</th><th>Ônibus</th><th>Motorista</th><th>Rota / avisos</th></tr>
        </ng-template>
        <ng-template #body let-v>
          <tr>
            <td>
              @for (f of v.faculdades; track f.id) { <p>{{ f.nome ?? 'Faculdade sem nome' }}</p> }
              @if (!v.faculdades.length) { <p>Destino não informado</p> }
            </td>
            <td><strong>{{ v.onibus?.numeroIdentificacao ?? 'Não definido' }}</strong></td>
            <td><strong>{{ v.motorista?.nome ?? 'Não definido' }}</strong></td>
            <td>
              {{ v.rota?.nome ?? 'Rota não definida' }}
              @for (item of v.avisos; track item) { <p class="alerta">{{ item }}</p> }
            </td>
          </tr>
        </ng-template>
      </p-table>

      <h3>{{ p.mensagem.assunto }}</h3>
      <pre class="mensagem">{{ p.mensagem.texto }}</pre>
      <p-button label="Enviar programação de hoje" icon="pi pi-envelope"
        [loading]="enviando()" [disabled]="!podeEnviar()" (onClick)="iniciarEnvio()" />
      <p-button label="Reenviar correção" severity="warn"
        [disabled]="!podeEnviar()" (onClick)="dialogoReenvio = true; motivo.reset()" />
    </article>
  } @else if (carregando()) {
    <p>Carregando programação…</p>
  }

  @if (pendente()) {
    <article class="cartao alerta">
      <p>Há uma solicitação sem confirmação. Confira o histórico ou repita a mesma solicitação.</p>
      <p-button label="Repetir mesma solicitação" [loading]="enviando()"
        [disabled]="enviando()" (onClick)="enviarPendente()" />
    </article>
  }

  @if (historico(); as h) {
    <article class="cartao">
      <h2>Histórico de lotes</h2>
      <p-table [value]="h.itens" [lazy]="true" [paginator]="true"
        [rows]="tamanho" [first]="pagina * tamanho" [totalRecords]="h.totalElementos"
        [rowsPerPageOptions]="[10, 20, 50]" [loading]="carregando()"
        (onPage)="paginar($event)">
        <ng-template #header>
          <tr><th>Dia</th><th>Origem</th><th>Resultado</th><th>Ações</th></tr>
        </ng-template>
        <ng-template #body let-lote>
          <tr>
            <td>{{ dataCivil(lote.dataReferencia) }}</td>
            <td>{{ lote.origem }} {{ lote.reenvio ? '(correção)' : '' }}</td>
            <td>
              Aceitos SMTP: {{ lote.totais.ACEITA_SMTP }} · Pendentes: {{ lote.totais.PENDENTE }}
              · Incertos: {{ lote.totais.INCERTA }} · Ignorados: {{ lote.ignorados }}
            </td>
            <td><p-button label="Detalhes" (onClick)="abrirDetalhe(lote.id)" /></td>
          </tr>
        </ng-template>
        <ng-template #emptymessage><tr><td colspan="4">Nenhum lote registrado.</td></tr></ng-template>
      </p-table>
    </article>
  }
</section>

<p-dialog header="Reenviar correção" [(visible)]="dialogoReenvio" [modal]="true"
  [style]="{ width: 'min(95vw, 36rem)' }">
  <p>Outra mensagem poderá ser enviada para quem já recebeu hoje.</p>
  <label for="motivo">Motivo obrigatório</label>
  <textarea id="motivo" rows="4" maxlength="500" [formControl]="motivo"></textarea>
  @if (motivo.touched && (motivo.invalid || !motivo.value.trim())) {
    <p role="alert">Informe um motivo com até 500 caracteres.</p>
  }
  <p-button label="Confirmar reenvio" severity="warn"
    [disabled]="motivo.invalid || !motivo.value.trim() || !podeEnviar()"
    (onClick)="confirmarReenvio()" />
</p-dialog>

<p-dialog header="Resultado do lote" [visible]="!!detalhe()" [modal]="true"
  (visibleChange)="!$event && detalhe.set(null)" [style]="{ width: 'min(95vw, 70rem)' }">
  @if (detalhe(); as lote) {
    <p>{{ lote.id }} — {{ dataCivil(lote.dataReferencia) }}</p>
    <p>Aceito pelo SMTP não comprova chegada à caixa de entrada.</p>
    @if (lote.motivo) { <p>Motivo: {{ lote.motivo }}</p> }
    <p-button label="Atualizar resultado" (onClick)="atualizarDetalhe()" />
    <p-table [value]="lote.entregas">
      <ng-template #header><tr><th>E-mail</th><th>Estado</th><th>Tentativas</th><th>Erro</th></tr></ng-template>
      <ng-template #body let-e>
        <tr><td>{{ e.email }}</td><td>{{ estado(e.estado) }}</td>
          <td>{{ e.tentativas }}</td><td>{{ e.erro ?? '—' }}</td></tr>
      </ng-template>
    </p-table>
    @if (lote.mensagem) {
      <h3>Mensagem efetivamente enfileirada</h3>
      <pre class="mensagem">{{ lote.mensagem.texto }}</pre>
    }
  }
</p-dialog>
```

O texto dentro de `pre` usa interpolação Angular, não HTML cru. Não usar `bypassSecurityTrustHtml`.
Se desejar prévia HTML, preferir reconstruir cartões a partir de `viagens`, ou renderizar HTML com a sanitização padrão do Angular sem desativá-la.

`email-programacao.scss`:

```scss
.email-painel { display: grid; gap: 1rem; }
header { display: flex; flex-wrap: wrap; align-items: center; gap: 1rem; }
header h1, header p { flex-basis: 100%; margin: 0; }
.cartao { padding: 1.25rem; border: 1px solid #cbd5e1; border-radius: .75rem; min-width: 0; }
.mensagem { white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; background: #f8fafc; padding: 1rem; }
.alerta { color: #92400e; }
.erro { color: #b91c1c; }
textarea { display: block; width: 100%; margin: .75rem 0; font: inherit; }
p-button { display: inline-block; margin: .3rem; }
```

Para tabelas largas, manter rolagem horizontal ou adaptar para cartões em telas pequenas. Não esconder os avisos do ônibus no mobile.

## 11. Atualização e interpretação dos resultados

POST 202 significa "registrado para processamento", não "e-mail entregue".

| Estado | Exibição recomendada |
|---|---|
| PENDENTE | Na fila |
| PROCESSANDO | Processando |
| ACEITA_SMTP | Aceito pelo servidor de e-mail; sem confirmação de entrega |
| FALHA | Falha conhecida |
| INCERTA | Pode ter sido enviado; conferir antes de reenviar |
| SUPRIMIDA | E-mail/acesso alterado, excluído ou conta inativa; não enviado |
| EXPIRADA | Janela de envio encerrada |

Não há webhook de entrega, confirmação de leitura, acompanhamento de abertura ou garantia de caixa de entrada nesta versão.
A mensagem pode ser encaminhada para spam ou rejeitada posteriormente pelo destino mesmo após aceite SMTP.

A fila reserva no máximo uma entrega por segundo. Somente falhas comprovadamente anteriores ao envio podem ser repetidas, até quatro tentativas totais, com intervalos de 1, 5 e 15 minutos.
Timeout/resultado ambíguo não é repetido automaticamente.

Reservas abandonadas por mais de dez minutos viram INCERTA, sem reenvio automático.
Entregas automáticas pendentes expiram às 18h. Manuais expiram na virada do dia de São Paulo.
Desativar o automático impede novos lotes automáticos; não cancela mensagens já enfileiradas ou aceitas.
Desabilitar `EMAIL_ENABLED` no servidor interrompe o processamento; isso não recolhe mensagens já enviadas.
Arquivar aluno suprime entregas que ainda não passaram pela revalidação. O worker usa `SUPRIMIDA` e `CONTA_OU_EMAIL_ALTERADO`. Existe uma pequena janela entre essa verificação e o SMTP: não há promessa de recolher ou cancelar envio já iniciado. Depois de restaurar, reativar a conta separadamente para voltar a ser elegível; não há retry automático das mensagens suprimidas.

O frontend pode adicionar atualização a cada cinco segundos enquanto houver PENDENTE/PROCESSANDO no detalhe.
Usar `timer` + `exhaustMap` + `takeUntilDestroyed`; parar ao fechar o diálogo/sair da página. Não criar polling global eterno.
O botão Atualizar já atende à primeira versão. O agendamento é do backend, não do navegador.

No detalhe, usar a mensagem congelada do lote, e não a prévia atual.
Alterar ônibus/grade depois do envio não modifica o e-mail enviado. Atualizar a prévia e solicitar correção explícita.

## 12. Tratamento de erros

| HTTP | Ação |
|---|---|
| 400 | indicar campos/formato inválidos, motivo vazio ou paginação inválida |
| 401 | deixar o interceptor encerrar a sessão; retornar ao login |
| 403 | informar exclusividade de ADMIN; não tentar com outra rota |
| 404 | lote inexistente; atualizar histórico |
| 409 | sem programação/destinatários ou UUID reutilizado com outros parâmetros; conferir mensagem |
| 503 | integração desativada/configuração incompleta |
| status 0 / timeout / 5xx | resultado da solicitação pode ser incerto; manter UUID e conferir histórico |

Diferenciar:
- falha ao registrar a solicitação HTTP;
- lote criado, mas entrega com falha SMTP;
- SMTP aceitou a mensagem, sem prova de entrega.

Nunca apresentar erro de consulta como "nenhuma viagem". Erros técnicos não são estados vazios.
Mensagens enviadas pelo servidor usam `ProblemDetail`, com `detail` e, na validação, `errors`.
Usar o utilitário de erros existente caso ele já trate esse formato.

## 13. Configuração externa do SMTP

A implementação usa Spring Mail, `JavaMailSender` e mensagens MIME com texto/HTML. Os timeouts são explícitos.
Referências: [Spring Boot — envio de e-mail](https://docs.spring.io/spring-boot/reference/io/email.html) e [Spring — MimeMessageHelper](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/mail/javamail/MimeMessageHelper.html).

No IntelliJ, em Run → Edit Configurations → Environment variables, configurar no BACKEND:

| Variável | Uso |
|---|---|
| EMAIL_ENABLED | false inicialmente; true somente para habilitar a integração |
| EMAIL_SCHEDULERS_ENABLED | true para rotina/fila; false apenas em testes |
| EMAIL_PRODUCTION | false para limitar destinatários; true para associados ativos em produção |
| EMAIL_TEST_RECIPIENTS | lista de e-mails separados por vírgula; devem existir como contas ativas |
| EMAIL_FROM | endereço de remetente autorizado no provedor |
| EMAIL_SMTP_HOST | servidor informado pelo provedor |
| EMAIL_SMTP_PORT | porta informada pelo provedor |
| EMAIL_SMTP_USERNAME | usuário SMTP |
| EMAIL_SMTP_PASSWORD | credencial SMTP específica, nunca a senha do sistema |
| EMAIL_STARTTLS | true para conexão STARTTLS |
| EMAIL_SSL | true para TLS implícito |

Configurar exatamente uma opção de TLS. Para STARTTLS, `EMAIL_STARTTLS=true` e `EMAIL_SSL=false`.
Para TLS implícito, inverter. Não escolher porta ou tipo de TLS por tentativa: usar os dados oficiais do provedor escolhido.
Ambos os campos de autenticação devem estar preenchidos, ou ambos vazios para um relay configurado para operar sem autenticação.

Passo a passo:

1. Escolher um provedor que ofereça SMTP para envio operacional e verificar seus requisitos/limites atuais.
2. Cadastrar/verificar o remetente e domínio conforme o provedor. Usar suas orientações de SPF/DKIM/DMARC, quando aplicáveis.
3. Criar credencial de envio restrita; alguns provedores usam senha de aplicativo ou credencial específica.
4. Não enviar credenciais no chat, imprimir em logs, versionar arquivos locais ou colocar no frontend.
5. Configurar as variáveis, mantendo produção false e automático desativado.
6. Incluir somente contas de teste controladas em EMAIL_TEST_RECIPIENTS.
7. Reiniciar o backend; conferir configuração e prévia como ADMIN.
8. Solicitar um envio manual; acompanhar fila/histórico e verificar caixa de entrada/spam no destinatário.
9. Conferir acentos, organização por faculdade, ônibus e motorista, sem lista de e-mails exposta.
10. Somente depois da validação, habilitar produção e automático explicitamente.

`disponivel=true` significa que a configuração mínima parece válida. Não é um teste de conexão nem prova de credencial aceita.
A aplicação não envia um e-mail de teste automaticamente ao iniciar.
Nenhum envio real ocorreu nos testes automatizados: o gateway é simulado.

## 14. Checklist manual

- [ ] ADMIN acessa a tela; ALUNO/MOTORISTA recebem 403 na API administrativa.
- [ ] Sem token, todos os endpoints da integração retornam 401.
- [ ] Configuração inicial está desativada e explica os impedimentos.
- [ ] Prévia mostra todas as grades do dia, não apenas uma grade.
- [ ] Segunda seleciona SEGUNDA; terça seleciona TERCA, independentemente da data antiga.
- [ ] Viagens sem grade não aparecem como programação confirmada.
- [ ] Múltiplos destinos aparecem em suas seções; pontos não se repetem pelo vínculo duplo.
- [ ] Nome da faculdade, número do ônibus e motorista são claros em desktop/mobile.
- [ ] Ônibus indisponível tem aviso, sem excluir automaticamente a viagem.
- [ ] Não há CPF, CNH, salário, mensalidade ou lista de outros alunos no corpo.
- [ ] Apenas contas ativas de alunos não arquivados e de motoristas são destinatárias; ADMIN fica fora.
- [ ] Pessoa sem conta não aparece como destinatário.
- [ ] Modo de teste respeita a lista permitida.
- [ ] Cada mensagem tem apenas um destinatário visível.
- [ ] Clique duplo não gera dois POSTs; repetição usa o mesmo UUID.
- [ ] POST 202 mostra processamento, não entrega confirmada.
- [ ] Reenvio exige confirmação e motivo não vazio.
- [ ] Manual anterior não é repetido automaticamente para o mesmo endereço no dia.
- [ ] Mudança de e-mail, desativação/exclusão antes do processamento suprime a entrega antiga.
- [ ] Falha de consulta não parece lista vazia.
- [ ] Histórico usa paginação do servidor; detalhe mostra conteúdo congelado.
- [ ] INCERTA alerta sobre possível duplicação e não dispara retry automático.
- [ ] Automático respeita São Paulo, recuperação antes das 18h e encerramento das pendências.
- [ ] Navegador fechado não impede a rotina; backend desligado não consegue enviar.
- [ ] SMTP real foi validado separadamente antes de habilitar produção/automático.

## 15. Testes Angular sugeridos

Com HttpTestingController/runner já existente:

- Serviço: URLs, métodos, parâmetros e corpos corretos; nenhuma credencial ou destinatário no POST.
- Guards/menu: somente ADMIN; não alterar menus dos associados.
- Carregamento: disponibilidade, avisos, ausência de programação e falha HTTP distinta de vazio.
- Envios: double-click bloqueado, UUID estável em retry, novo UUID somente para nova ação.
- Reenvio: motivo obrigatório/max 500/sem espaços somente e confirmação explícita.
- sessionStorage: solicitação pendente restaurada e separada por administrador.
- Automático: mantém estado anterior em falha; permite desligar quando indisponível.
- Histórico: paginação real, detalhe atualizado, mensagem congelada e todos os estados.
- Datas: yyyy-MM-dd sem deslocamento de fuso.
- Privacidade: interpolação segura; HTML malicioso cadastrado não executa.
- Saída da tela: assinaturas/polling encerrados e nenhuma requisição de envio iniciada por timer frontend.

Executar testes e build do Angular antes de entregar a interface.
Este arquivo não afirma que o frontend foi implementado ou validado nesta entrega.
