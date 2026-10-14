# Programação individual por WhatsApp — integração no frontend

Referência: backend implementado em 14/10/2026. O Angular **não foi alterado** nesta entrega.

## 1. O que foi implementado

O backend gera a programação individual do dia, produz PDF e envia um template com documento pela WhatsApp Cloud API oficial da Meta.

- ALUNO: viagens que atendem explicitamente à faculdade do cadastro.
- MOTORISTA: somente viagens atribuídas à sua pessoa.
- Recebimento exige conta ativa, aceite e confirmação pelo próprio WhatsApp.
- Matrícula pendente/inativa não bloqueia o recebimento se a conta de acesso estiver ativa.
- Datas antigas de viagem são ignoradas. O dia da grade define a programação.
- Sem programação, nenhum aviso/PDF vazio é enviado.
- Ônibus indisponível aparece com aviso, sem cancelamento automático.
- Automático: 15h, America/Sao_Paulo, com recuperação até antes das 18h.
- Envio manual normal impede repetição diária para os mesmos destinatários.
- Correções exigem reenvio explícito, com motivo.

Os PDFs não expõem CPF, CNH, salários, cobranças ou outros alunos. Não há grupo, WhatsApp Web, rastreamento, horários de saída ou chatbot geral.

**Importante:** o código não substitui a criação da conta Meta, o template aprovado, o webhook HTTPS e as credenciais. A integração começa desativada, em modo de teste; nenhum envio real foi feito nos testes do projeto.

## 2. Preparar a Meta e o backend

### 2.1 Conta e ambiente de teste

1. Acessar [Meta for Developers](https://developers.facebook.com/) e criar/configurar um aplicativo para WhatsApp Business.
2. Associar a conta WhatsApp Business — WABA — e obter o número de teste.
3. Anotar WABA ID e Phone Number ID. O Phone Number ID **não** é o número de telefone.
4. Registrar na Meta os destinatários de teste permitidos. Usar somente números controlados por pessoas que autorizaram o teste.
5. Obter o token de acesso, App Secret e versão Graph API suportada pela conta.
6. Publicar uma URL HTTPS para o backend; em desenvolvimento, usar um túnel seguro explicitamente configurado. Backend local sem URL pública não recebe webhooks da Meta.

Referência de requisições: [coleção oficial da Meta](https://www.postman.com/meta/whatsapp-business-platform/overview).

Não comprar provedor nem migrar um número pessoal sem conferir as condições da conta. A primeira validação deve usar o número de teste. O número de produção e as tarifas são uma etapa posterior.

### 2.2 Template com PDF

Criar na Meta o template configurado no backend, por exemplo:

- Nome: `programacao_transporte`.
- Idioma: `pt_BR`.
- Cabeçalho: mídia **DOCUMENT**, com PDF de exemplo.
- Corpo: exatamente três parâmetros posicionais.
- Solicitar categoria de serviço/utility apropriada; a classificação/aprovação final é da Meta.

Texto sugerido:

```text
Olá, {{1}}. Segue sua programação de transporte de {{2}}, referente a {{3}}.
Consulte o PDF. Para cancelar o recebimento, responda SAIR.
```

Parâmetros:

1. Nome do destinatário.
2. Data da programação em dd/MM/yyyy.
3. Faculdade do aluno ou “Suas viagens atribuídas” para motorista.

Não adicionar um quarto parâmetro ou alterar o cabeçalho para imagem. O backend verifica número empresarial, nome, idioma, aprovação e formato do template antes de habilitar o automático ou solicitar um lote.

O PDF é enviado por upload de mídia e identificado pelo media ID. Não precisamos publicar PDFs em links abertos.

### 2.3 Variáveis externas

Configurar no processo Java/IntelliJ ou servidor, nunca no Angular ou no Git:

| Variável | Uso |
|---|---|
| WHATSAPP_ENABLED | false inicialmente; true somente para configurar/testar a integração |
| WHATSAPP_PRODUCTION | false no ambiente de teste |
| WHATSAPP_SCHEDULERS_ENABLED | true para rotinas; false em testes automatizados |
| WHATSAPP_GRAPH_API_VERSION | Versão suportada, no formato vN.N |
| WHATSAPP_ACCESS_TOKEN | Token de acesso da Meta |
| WHATSAPP_WABA_ID | ID da conta empresarial |
| WHATSAPP_PHONE_NUMBER_ID | ID do número empresarial |
| WHATSAPP_BUSINESS_NUMBER | Número internacional do sistema, ex.: +5511999999999 |
| WHATSAPP_APP_SECRET | Segredo do aplicativo para validar assinatura dos webhooks |
| WHATSAPP_VERIFY_TOKEN | Segredo definido por você para o desafio de verificação |
| WHATSAPP_TEMPLATE_NAME | Nome do template aprovado |
| WHATSAPP_TEST_ALLOWED_NUMBERS | Números internacionais permitidos, separados por vírgula |

O Verify Token é diferente do token de acesso e do App Secret. Não enviar credenciais pelo formulário frontend, imprimir tokens em logs ou inserir segredos em mensagens compartilhadas.

Exemplo estrutural, **com valores fictícios**:

```text
WHATSAPP_ENABLED=true
WHATSAPP_PRODUCTION=false
WHATSAPP_GRAPH_API_VERSION=<versão suportada na conta Meta>
WHATSAPP_BUSINESS_NUMBER=+5511999999999
WHATSAPP_TEST_ALLOWED_NUMBERS=+5511999990001,+5511999990002
```

As outras credenciais devem ser preenchidas externamente. Não copiar os números fictícios como destinatários reais.

### 2.4 Webhook

Configurar a callback URL:

```text
https://<seu-backend>/webhooks/whatsapp
```

1. Informar na Meta o mesmo WHATSAPP_VERIFY_TOKEN do servidor.
2. A Meta faz GET com hub.mode, hub.verify_token e hub.challenge.
3. O backend retorna o desafio e registra a verificação.
4. Assinar os eventos de mensagens/status disponíveis na conta.
5. Os POSTs são aceitos somente com X-Hub-Signature-256 válido para o corpo original.

O webhook não usa JWT. Não adicionar Bearer no painel da Meta. CORS do Angular não controla chamadas servidor-servidor.

Mudança do App Secret, número ou Verify Token invalida a verificação local anterior e exige novo desafio. A resposta do endpoint administrativo indica se o webhook está verificado.

### 2.5 Primeira validação real

1. Aplicar V6 pela inicialização normal do backend, após backup em ambiente importante.
2. Configurar credenciais e verificar webhook.
3. Confirmar uma assinatura de aluno/motorista com programação no dia.
4. Conferir prévia e PDF.
5. Solicitar um lote manual com poucos destinatários permitidos.
6. Verificar entrega pelo webhook e no telefone.
7. Só depois ativar automático.

O cadastro de consentimento e o cancelamento seguem a [política oficial](https://whatsappbusiness.com/policy/). Não presumir que os envios serão gratuitos nem que cadastrar um telefone já equivale a autorização.

## 3. Organização sugerida no Angular atual

O projeto usa Angular standalone/PrimeNG. Reutilizar:

- `src/app/core/auth/service/auth.service.ts` e o interceptor JWT existente.
- `src/app/core/auth/guards/auth.guards.ts`.
- `src/app/core/auth/utils/api-error.ts`, função mensagemApi.
- `src/app/components/perfil/components/perfil/`.
- `src/app/components/sidebar/components/sidebar/`.

Adicionar uma feature seguindo as pastas atuais:

```text
src/app/components/whatsapp/
  models/whatsapp.models.ts
  service/whatsapp-service.ts
  components/assinatura-whatsapp/assinatura-whatsapp.ts
  components/assinatura-whatsapp/assinatura-whatsapp.html
  components/whatsapp-admin/whatsapp-admin.ts
  components/whatsapp-admin/whatsapp-admin.html
```

Não duplicar autenticação, guardar JWT novo ou chamar a Meta do navegador. A identidade pessoal é derivada do JWT no backend; nenhum endpoint pessoal aceita usuarioId/pessoaId do cliente.

## 4. Interfaces TypeScript

```ts
import { ViagemResumo } from '../../dashboard/models/dashboard.models';

export type StatusAssinatura = 'NAO_CADASTRADA' | 'PENDENTE' | 'ATIVA' | 'CANCELADA';
export type PublicoWhatsapp = 'TODOS' | 'ALUNOS' | 'MOTORISTAS';
export type OrigemWhatsapp = 'MANUAL' | 'AUTOMATICA';
export type StatusEntrega =
  | 'PENDENTE' | 'PROCESSANDO' | 'ACEITA' | 'ENVIADA' | 'ENTREGUE' | 'LIDA'
  | 'FALHA' | 'INCERTA' | 'SUPRIMIDA' | 'EXPIRADA';

export interface AssinaturaWhatsapp {
  numero: string | null;
  status: StatusAssinatura;
  consentimentoEm: string | null;
  confirmadoEm: string | null;
  canceladoEm: string | null;
  codigoExpiraEm: string | null;
}
export interface AtivacaoWhatsapp {
  assinatura: AssinaturaWhatsapp;
  linkAtivacao: string;
}
export interface ConfiguracaoWhatsapp {
  integracaoHabilitada: boolean;
  modoProducao: boolean;
  automatico: boolean;
  credenciaisConfiguradas: boolean;
  webhookVerificado: boolean;
  templateVerificado: boolean;
  horario: string;
  fuso: string;
  recuperacaoAte: string;
  numeroSistema: string | null;
}
export interface ProgramacaoWhatsapp {
  dataReferencia: string;
  diaSemana: string;
  papel: 'ALUNO' | 'MOTORISTA';
  referencia: string;
  viagens: ViagemResumo[];
}
export interface PreviaWhatsapp {
  usuarioId: number;
  nome: string | null;
  elegivel: boolean;
  impedimentos: string[];
  programacao: ProgramacaoWhatsapp;
}
export interface SolicitarEnvioWhatsapp {
  requestId: string;
  publico: PublicoWhatsapp;
  reenviar: boolean;
  motivo: string | null;
}
export interface EntregaWhatsapp {
  id: string;
  usuarioId: number; // ID original preservado no histórico após exclusão.
  nome: string;
  numeroMascarado: string;
  revisao: number;
  status: StatusEntrega;
  tentativas: number;
  erro: string | null;
  atualizadoEm: string | null;
}
export interface LoteWhatsapp {
  id: string;
  dataReferencia: string;
  origem: OrigemWhatsapp;
  publico: PublicoWhatsapp;
  reenvio: boolean;
  motivo: string | null;
  criadoEm: string;
  ignoradosDuplicidade: number;
  totais: Record<StatusEntrega, number>;
  entregas: EntregaWhatsapp[];
}
export interface LoteResumoWhatsapp {
  id: string;
  dataReferencia: string;
  origem: OrigemWhatsapp;
  publico: PublicoWhatsapp;
  reenvio: boolean;
  criadoEm: string;
}
export interface HistoricoWhatsapp {
  itens: LoteResumoWhatsapp[];
  pagina: number;
  tamanho: number;
  total: number;
  totalPaginas: number;
}
```

ViagemResumo é o contrato do guia de dashboard, não a entidade antiga de viagem. Se o módulo ainda não foi implementado, criar os tipos compartilhados descritos naquele guia antes de importar.

**Atenção:** request usa reenviar; resposta usa reenvio. Datas de referência são LocalDate; timestamps têm offset/UTC. Números internacionais são strings, não números JavaScript.

## 5. Serviço HTTP completo

Criar `service/whatsapp-service.ts`:

```ts
import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  AssinaturaWhatsapp, AtivacaoWhatsapp, ConfiguracaoWhatsapp,
  PreviaWhatsapp, SolicitarEnvioWhatsapp, LoteWhatsapp, HistoricoWhatsapp,
} from '../models/whatsapp.models';

@Injectable({ providedIn: 'root' })
export class WhatsappService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  minhaAssinatura(): Observable<AssinaturaWhatsapp> {
    return this.http.get<AssinaturaWhatsapp>(`${this.api}/whatsapp/minha-assinatura`);
  }
  solicitarAtivacao(numero: string, aceite: boolean): Observable<AtivacaoWhatsapp> {
    return this.http.post<AtivacaoWhatsapp>(`${this.api}/whatsapp/minha-assinatura/ativacao`, { numero, aceite });
  }
  cancelar(): Observable<void> {
    return this.http.delete<void>(`${this.api}/whatsapp/minha-assinatura`);
  }
  meuPdf(): Observable<Blob> {
    return this.http.get(`${this.api}/whatsapp/minha-programacao/pdf`, { responseType: 'blob' });
  }
  configuracao(): Observable<ConfiguracaoWhatsapp> {
    return this.http.get<ConfiguracaoWhatsapp>(`${this.api}/integracoes/whatsapp/configuracao`);
  }
  configurarAutomatico(automatico: boolean): Observable<ConfiguracaoWhatsapp> {
    return this.http.put<ConfiguracaoWhatsapp>(`${this.api}/integracoes/whatsapp/configuracao`, { automatico });
  }
  previa(usuarioId: number): Observable<PreviaWhatsapp> {
    const params = new HttpParams().set('usuarioId', usuarioId);
    return this.http.get<PreviaWhatsapp>(`${this.api}/integracoes/whatsapp/previa`, { params });
  }
  previaPdf(usuarioId: number): Observable<Blob> {
    const params = new HttpParams().set('usuarioId', usuarioId);
    return this.http.get(`${this.api}/integracoes/whatsapp/previa/pdf`, { params, responseType: 'blob' });
  }
  enviar(dados: SolicitarEnvioWhatsapp): Observable<LoteWhatsapp> {
    return this.http.post<LoteWhatsapp>(`${this.api}/integracoes/whatsapp/envios`, dados);
  }
  historico(pagina = 0, tamanho = 20): Observable<HistoricoWhatsapp> {
    const params = new HttpParams().set('pagina', pagina).set('tamanho', tamanho);
    return this.http.get<HistoricoWhatsapp>(`${this.api}/integracoes/whatsapp/envios`, { params });
  }
  lote(id: string): Observable<LoteWhatsapp> {
    return this.http.get<LoteWhatsapp>(`${this.api}/integracoes/whatsapp/envios/${encodeURIComponent(id)}`);
  }
}
```

O interceptor atual adiciona Bearer. Nenhum método frontend envia valor financeiro, PDF para a Meta, credencial, autor de consentimento ou data arbitrária de envio.

## 6. Perfil: confirmação e cancelamento

Adicionar AssinaturaWhatsapp ao perfil apenas para ALUNO/MOTORISTA:

```html
@if (auth.temPapel('ALUNO', 'MOTORISTA')) {
  <app-assinatura-whatsapp />
}
```

Importar o componente na lista imports de Perfil. Não substituir os dados pessoais atuais.

### Formulário

Usar Reactive Forms:

- numero: obrigatório, string internacional; exemplo +5511999999999.
- aceite: false inicialmente, Validators.requiredTrue.
- Botão “Confirmar meu WhatsApp”.
- Explicação: programação diária, número do sistema, PDF e cancelamento.

Exemplo do formulário:

```ts
readonly form = new FormGroup({
  numero: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  aceite: new FormControl(false, { nonNullable: true, validators: [Validators.requiredTrue] }),
});
readonly assinatura = signal<AssinaturaWhatsapp | null>(null);
readonly linkAtivacao = signal<string | null>(null);
readonly salvando = signal(false);
readonly erro = signal<string | null>(null);

solicitar(): void {
  if (this.form.invalid || this.salvando()) { this.form.markAllAsTouched(); return; }
  this.salvando.set(true);
  this.erro.set(null);
  const { numero, aceite } = this.form.getRawValue();
  this.service.solicitarAtivacao(numero, aceite).pipe(
    takeUntilDestroyed(this.destroyRef),
    finalize(() => this.salvando.set(false)),
  ).subscribe({
    next: resposta => {
      this.assinatura.set(resposta.assinatura);
      this.linkAtivacao.set(resposta.linkAtivacao);
    },
    error: (error: HttpErrorResponse) => this.erro.set(mensagemApi(error)),
  });
}
```

Esse trecho pertence ao componente standalone, com service = inject(WhatsappService), destroyRef = inject(DestroyRef), imports de ReactiveFormsModule e módulos PrimeNG usados. Importar FormGroup/FormControl/Validators, signal/inject, takeUntilDestroyed e finalize.

Após a resposta, exibir um link que o usuário deverá clicar:

```html
@if (linkAtivacao(); as link) {
  <p-message severity="info">
    Abra o WhatsApp e envie a mensagem de ativação. Só abrir a conversa não confirma o número.
  </p-message>
  <a [href]="link" target="_blank" rel="noopener noreferrer">Abrir WhatsApp para confirmar</a>
}
```

Não abrir automaticamente uma janela depois do HTTP, pois pode ser bloqueada como popup. Não extrair ou alterar o código do link. Não guardar o link em localStorage/sessionStorage, logs ou analytics.

### Estados

| Situação | Interface |
|---|---|
| NAO_CADASTRADA | Formulário de número e aceite |
| PENDENTE | Número e prazo de 30 minutos; link e botão “Atualizar situação” |
| ATIVA | “Você receberá a programação dos dias com viagens”; número e cancelamento |
| CANCELADA | Recebimento desativado; nova ativação exige consentimento/confirmação |

Consultar GET minha-assinatura ao entrar e após atualizar/cancelar. Durante confirmação, pode usar polling a cada cinco segundos somente enquanto o componente estiver aberto e a assinatura pendente, limitado a dez minutos; parar ao confirmar/cancelar, no erro 401 ou ao destruir o componente. Nunca refazer POST de ativação automaticamente.

O backend permite cinco pedidos de ativação por conta/hora. Trocar o número suspende o anterior e invalida códigos antigos. Não concluir confirmação pelo frontend: somente o webhook muda para ATIVA.

Cancelamento chama DELETE, exige confirmação visual e limpa o link local. Também é possível responder SAIR, PARAR ou CANCELAR no WhatsApp. Cancelar não recolhe PDFs/mensagens já enviados.

## 7. Administração: prévia, envio e automático

### Rota e menu

Adicionar rota administrativa:

```ts
{
  path: 'whatsapp',
  loadComponent: () =>
    import('./components/whatsapp/components/whatsapp-admin/whatsapp-admin').then(m => m.WhatsappAdmin),
  canActivate: [roleGuard(['ADMIN'])],
},
```

Adicionar “WhatsApp” na sidebar apenas do ADMIN. Pode haver atalho “Enviar programação de hoje” em grades; o atalho abre a mesma tela/serviço, sem duplicar a implementação de envio.

### Layout recomendado

1. Estado da integração: habilitada, teste/produção, credenciais, webhook e template.
2. Automático: chave ativar/desativar, 15h e recuperação até antes das 18h.
3. Prévia individual.
4. Envio manual/reenvio.
5. Histórico paginado e detalhes das entregas.

Nenhum formulário de tokens/segredos. A UI lê somente ConfiguracaoWhatsapp. Mostrar “Aguardando configuração da Meta” quando não estiver pronta; não exibir campos de credencial vazios.

### Prévia

Reutilizar a lista segura de GET /usuarios, filtrando ALUNO/MOTORISTA, para o seletor de usuário. Somente ADMIN usa usuarioId neste recurso.

- Consultar previa(usuarioId).
- Mostrar programação.dataReferencia, diaSemana e referencia.
- Mostrar as viagens com ônibus, motorista, rota, pontos, faculdades e avisos.
- Mostrar elegibilidade e impedimentos.
- Botão “Baixar PDF da prévia” usa previaPdf(usuarioId).

O PDF pessoal ou a prévia administrativa pode ser consultado sem assinatura ativa. Isso não autoriza envio. Elegibilidade descreve destinatário/programação; disponibilidade da integração deve ser conferida separadamente na configuração.

Impedimentos atuais: CONTA_INATIVA, WHATSAPP_NAO_CONFIRMADO, NUMERO_FORA_DA_LISTA_DE_TESTE, SEM_FACULDADE, SEM_PROGRAMACAO.

### Lote manual e idempotência

Selecionar público TODOS, ALUNOS ou MOTORISTAS. A data é sempre o dia atual de São Paulo; não oferecer datas passadas/futuras.

Exemplo:

```json
{
  "requestId": "592ac47c-c08c-419e-8bfb-c40f8f75a893",
  "publico": "ALUNOS",
  "reenviar": false,
  "motivo": null
}
```

Gerar requestId com crypto.randomUUID() ao criar a intenção de envio. Se houver falha de conexão e o resultado for desconhecido, repetir a **mesma intenção com o mesmo UUID**, não gerar UUID novo às cegas.

Bloquear clique duplo com signal enviando. Após HTTP 202, mostrar “Lote registrado para processamento”, guardar o ID do lote e consultar os detalhes. Não mostrar “Mensagens entregues” na resposta do POST.

Mesmo um novo UUID não repete a programação normal já preparada para o mesmo destinatário/dia. A resposta traz ignoradosDuplicidade. Assinaturas canceladas/inativas não entram na lista de envio; assinaturas ativas sem programação ficam como SUPRIMIDA.

Reenvio:

- Ação separada “Enviar atualização”.
- Confirmação: nova mensagem será enviada e a anterior não será apagada.
- Motivo obrigatório, até 500 caracteres.
- Novo requestId e reenviar=true.
- Nova revisão por destinatário, sem sobrescrever o snapshot anterior.

Se o lote não tiver destinatários elegíveis, mostrar os totais reais, não inventar envio. Permitir atualizar prévia/configuração antes de uma nova ação.

### Automático

PUT configuração com:

```json
{ "automatico": true }
```

O backend verifica as credenciais, o webhook e o template antes de habilitar. Se falhar, manter o valor anterior e mostrar a mensagem; não deixar a chave visual ativada.

Desativar usa automatico=false e não depende da disponibilidade da Meta. Essa chave para criação automática de lotes, não cancela mensagens já aceitas nem desfaz lotes manuais.

Se o sistema voltar entre 15h e antes das 18h, prepara o lote daquele dia ainda não registrado. Depois das 18h, não recupera automaticamente o dia perdido. O ADMIN pode enviar manualmente no próprio dia.

Backend precisa estar ligado/hospedado; fechar o Angular não interrompe o automático. Não implementar timers de envio no navegador.

## 8. Histórico e estados de entrega

GET envios usa pagina iniciando em zero e tamanho entre 1 e 100, padrão 20. Resposta: itens, pagina, tamanho, total e totalPaginas.

Na tabela PrimeNG, ligar paginação do servidor:

- totalRecords = resposta.total.
- primeira página = 0.
- página = Math.floor(first / rows).
- Ao alterar tamanho, voltar à página zero.
- Não ordenar somente uma página local e apresentar isso como ordenação de todo o histórico.

Detalhes do lote mostram nome, número mascarado, revisão, tentativas, estado e erro seguro. Não tentar recuperar telefone completo pelo histórico.

| Estado | Texto sugerido |
|---|---|
| PENDENTE | Aguardando processamento |
| PROCESSANDO | Preparando/envio em andamento |
| ACEITA | Aceita pela Meta, entrega ainda não confirmada |
| ENVIADA | Enviada segundo o webhook |
| ENTREGUE | Entregue segundo o webhook |
| LIDA | Leitura confirmada |
| FALHA | Falha conhecida |
| INCERTA | Resultado incerto; não repetir automaticamente |
| SUPRIMIDA | Não enviada por elegibilidade/cancelamento |
| EXPIRADA | Janela de envio encerrada |

Erros sanitizados como META_HTTP_429 ou TEMPLATE_DOCUMENTO_NAO_APROVADO ajudam no diagnóstico; não mostrar resposta bruta da Meta ou token. Não transformar ACEITA em ENTREGUE no frontend.

Atualizar histórico após solicitação. Nos detalhes, botão Atualizar; opcional polling a cada cinco segundos enquanto houver PENDENTE/PROCESSANDO/ACEITA/ENVIADA, limitado a dez minutos e encerrado ao sair da tela. Não usar polling para refazer POST.

Reservas abandonadas viram INCERTA após dez minutos, sem repetição. Falhas comprovadamente anteriores ao aceite têm até três novas tentativas, depois de 1, 5 e 15 minutos, respeitando a janela. Cancelamento/alteração de número/conta é conferido novamente antes da mensagem.

## 9. PDF autenticado e tratamento de erro Blob

O download usa HttpClient com responseType blob e o interceptor JWT. Não colocar token na query, não abrir a URL do backend diretamente e não criar um link público.

```ts
baixarPdf(): void {
  if (this.baixando()) return;
  this.baixando.set(true);
  this.service.meuPdf().pipe(
    takeUntilDestroyed(this.destroyRef),
    finalize(() => this.baixando.set(false)),
  ).subscribe({
    next: blob => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'programacao.pdf';
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    },
    error: (error: HttpErrorResponse) => { void this.erroPdf(error); },
  });
}

private async erroPdf(error: HttpErrorResponse): Promise<void> {
  let mensagem = mensagemApi(error);
  if (error.error instanceof Blob) {
    try {
      const problema = JSON.parse(await error.error.text()) as { detail?: unknown };
      if (typeof problema.detail === 'string') mensagem = problema.detail;
    } catch { /* Mantém mensagem segura já calculada. */ }
  }
  this.erro.set(mensagem);
}
```

Usar a mesma rotina de Blob para prévia administrativa. Os signals baixando/erro e destroyRef seguem o padrão dos componentes existentes. Um 404 significa ausência de programação/documento; não criar um PDF vazio no navegador.

## 10. Exemplos de respostas

Sem assinatura:

```json
{
  "numero": null,
  "status": "NAO_CADASTRADA",
  "consentimentoEm": null,
  "confirmadoEm": null,
  "canceladoEm": null,
  "codigoExpiraEm": null
}
```

Configuração inicial sem credenciais:

```json
{
  "integracaoHabilitada": false,
  "modoProducao": false,
  "automatico": false,
  "credenciaisConfiguradas": false,
  "webhookVerificado": false,
  "templateVerificado": false,
  "horario": "15:00",
  "fuso": "America/Sao_Paulo",
  "recuperacaoAte": "18:00",
  "numeroSistema": null
}
```

Lote ilustrativo com um destinatário, ainda na fila:

```json
{
  "id": "592ac47c-c08c-419e-8bfb-c40f8f75a893",
  "dataReferencia": "2026-10-14",
  "origem": "MANUAL",
  "publico": "ALUNOS",
  "reenvio": false,
  "motivo": null,
  "criadoEm": "2026-10-14T18:00:00Z",
  "ignoradosDuplicidade": 0,
  "totais": { "PENDENTE": 1, "PROCESSANDO": 0, "ACEITA": 0, "ENVIADA": 0, "ENTREGUE": 0, "LIDA": 0, "FALHA": 0, "INCERTA": 0, "SUPRIMIDA": 0, "EXPIRADA": 0 },
  "entregas": [{
    "id": "d4ac47cb-b750-4b43-b40a-c40f8f75a893",
    "usuarioId": 2,
    "nome": "Ana",
    "numeroMascarado": "***0001",
    "revisao": 0,
    "status": "PENDENTE",
    "tentativas": 0,
    "erro": null,
    "atualizadoEm": "2026-10-14T18:00:00Z"
  }]
}
```

## 11. Erros, datas e segurança

| Resposta | Tratamento |
|---|---|
| 400 | Revisar número, aceite, motivo, filtros ou parâmetros |
| 401 | Interceptor encerra sessão; assinatura inválida de webhook também retorna 401, mas não é chamada pelo frontend |
| 403 | Papel insuficiente; usar tela de sem-permissão |
| 404 | Usuário/lote inexistente ou PDF sem programação |
| 409 | Número já ativo, limite de ativações ou requestId com parâmetros diferentes |
| 503 | Integração desativada, configuração/webhook/template indisponível |
| status 0/5xx | Não assumir que POST falhou antes de gravar; preservar requestId e consultar histórico |

Falha de GET não vira histórico vazio. Usar skeleton, estado de erro e botão Atualizar separados de ausência real de registros.

LocalDate: DatePipe com UTC ou separar os componentes do texto, evitando deslocamento de dia. Timestamps podem ser exibidos com Intl.DateTimeFormat e timeZone America/Sao_Paulo.

Guards/botões não substituem o backend. Usar interpolação Angular em descrições/avisos; não inserir HTML enviado por usuários. Segredos e códigos de ativação não devem aparecer em analytics.

## 12. Checklist e testes frontend

- [ ] Interface de assinatura somente em perfil ALUNO/MOTORISTA.
- [ ] Sem aceite, nenhum pedido de ativação.
- [ ] Número internacional, confirmação real pelo WhatsApp e prazo exibido.
- [ ] Abrir a conversa não marca ATIVA; apenas a resposta do backend confirma.
- [ ] Troca de número e expiração exigem nova ativação.
- [ ] Cancelamento pelo perfil/WhatsApp interrompe novos envios.
- [ ] ADMIN não ativa consentimento de outra pessoa.
- [ ] Prévia mostra apenas a faculdade/atribuições corretas.
- [ ] Datas antigas não são usadas para a programação.
- [ ] Fim de semana sem grade não gera aviso/PDF vazio.
- [ ] PDF autenticado, com acentos, paginação e sem dados sensíveis.
- [ ] POST manual não é repetido com UUID novo após erro incerto.
- [ ] Manual/automático não duplicam destinatários no dia.
- [ ] Reenvio exige confirmação e motivo.
- [ ] HTTP 202 não é apresentado como entrega.
- [ ] Histórico é paginado no servidor e diferencia todos os estados.
- [ ] Chave automática só muda após sucesso do PUT.
- [ ] Ausência de credenciais não impede usar as telas antigas do sistema.
- [ ] Teste real limitado aos números autorizados e com conta Meta configurada.

Testes Angular sugeridos: HttpTestingController para URLs/corpos/Blob; formulário sem aceite; renderização dos estados; polling encerrado ao sair; manutenção do UUID após erro; paginação zero-based; erros Blob em PDF; menu por papel; chave automática em falha 503; contadores reais e ausência de “entregue” no 202.

Antes de entregar o frontend, executar seus testes e build. Esta entrega contém backend e guia, não uma interface já instalada ou uma conta Meta criada.
