package com.example.integrador.Email;

import com.example.integrador.DTO.DashboardResponse.ViagemResumo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EmailDto {
    private EmailDto() {
    }

    public enum Origem {MANUAL, AUTOMATICA}

    public enum Estado {PENDENTE, PROCESSANDO, ACEITA_SMTP, FALHA, INCERTA, SUPRIMIDA, EXPIRADA}

    public record ConfiguracaoRequest(@NotNull Boolean automatico) {
    }

    public record EnvioRequest(@NotNull UUID requestId, boolean reenviar, @Size(max = 500) String motivo) {
    }

    public record ConfiguracaoResponse(boolean habilitado, boolean automatico, boolean modoTeste,
                                       boolean disponivel, String horario, String fuso, List<String> impedimentos) {
    }

    public record Conteudo(String assunto, String texto, String html) {
    }

    public record Previa(LocalDate dataReferencia, Instant geradoEm, long destinatariosElegiveis,
                         long destinatariosIgnorados, List<ViagemResumo> viagens, List<String> avisos,
                         Conteudo mensagem) {
    }

    public record EntregaResponse(UUID id, Long usuarioId, String email, int revisao, Estado estado,
                                  int tentativas, String erro, Instant aceitoEm) {
    }

    public record LoteResponse(UUID id, LocalDate dataReferencia, Origem origem, boolean reenvio,
                               String motivo, Instant criadoEm, int ignorados, Map<Estado, Long> totais,
                               List<EntregaResponse> entregas, Conteudo mensagem) {
    }

    public record Historico(int pagina, int tamanho, long totalElementos, int totalPaginas,
                            List<LoteResponse> itens) {
    }

    public record Destinatario(Long usuarioId, String email) {
    }
}
