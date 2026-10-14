package com.example.integrador.Whatsapp;

import com.example.integrador.DTO.DashboardResponse.ViagemResumo;
import com.example.integrador.Enum.TipoPessoa;
import com.example.integrador.Whatsapp.WhatsappTipos.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public final class WhatsappDto {
    private WhatsappDto() {}
    public record AtivacaoRequest(@NotBlank @Size(max = 40) String numero, @NotNull Boolean aceite) {}
    public record ConfiguracaoRequest(@NotNull Boolean automatico) {}
    public record EnvioRequest(@NotNull UUID requestId, @NotNull Publico publico, boolean reenviar, @Size(max = 500) String motivo) {}
    public record AssinaturaResponse(String numero, String status, Instant consentimentoEm, Instant confirmadoEm, Instant canceladoEm, Instant codigoExpiraEm) {}
    public record AtivacaoResponse(AssinaturaResponse assinatura, String linkAtivacao) {}
    public record ConfiguracaoResponse(boolean integracaoHabilitada, boolean modoProducao, boolean automatico,
            boolean credenciaisConfiguradas, boolean webhookVerificado, boolean templateVerificado,
            String horario, String fuso, String recuperacaoAte, String numeroSistema) {}
    public record Programacao(LocalDate dataReferencia, String diaSemana, TipoPessoa papel, String referencia, List<ViagemResumo> viagens) {}
    public record PreviaResponse(Long usuarioId, String nome, boolean elegivel, List<String> impedimentos, Programacao programacao) {}
    public record EntregaResponse(UUID id, Long usuarioId, String nome, String numeroMascarado, int revisao,
            StatusEntrega status, int tentativas, String erro, Instant atualizadoEm) {}
    public record LoteResponse(UUID id, LocalDate dataReferencia, Origem origem, Publico publico,
            boolean reenvio, String motivo, Instant criadoEm, int ignoradosDuplicidade,
            Map<StatusEntrega, Long> totais, List<EntregaResponse> entregas) {}
    public record HistoricoResponse(List<LoteResumo> itens, int pagina, int tamanho, long total, int totalPaginas) {}
    public record LoteResumo(UUID id, LocalDate dataReferencia, Origem origem, Publico publico, boolean reenvio, Instant criadoEm) {}
}
