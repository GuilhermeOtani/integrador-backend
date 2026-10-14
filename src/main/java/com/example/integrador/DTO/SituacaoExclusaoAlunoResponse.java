package com.example.integrador.DTO;

import java.util.List;

public record SituacaoExclusaoAlunoResponse(Long alunoId, boolean arquivado, boolean podeExcluir,
        boolean possuiAcesso, Vinculos vinculos, List<Impedimento> impedimentos) {
    public record Vinculos(long contasReceber, long contasPagar, long chamados, long viagens) {
        public boolean possuiImpedimentos() { return contasReceber + contasPagar + chamados + viagens > 0; }
    }
    public record Impedimento(String codigo, String mensagem) {}
}
