package com.example.integrador.Service;

import com.example.integrador.Repository.AlunoRepository;
import com.example.integrador.Repository.MotoristaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RotinaFinanceiraService {
    private final MotoristaRepository motoristas;
    private final AlunoRepository alunos;
    private final ContaPagarService contasPagar;
    private final ContaReceberService contasReceber;
    public RotinaFinanceiraService(MotoristaRepository motoristas, AlunoRepository alunos,
                                  ContaPagarService contasPagar, ContaReceberService contasReceber) {
        this.motoristas = motoristas; this.alunos = alunos;
        this.contasPagar = contasPagar; this.contasReceber = contasReceber;
    }
    @Scheduled(cron = "0 0 2 * * *", zone = "America/Sao_Paulo")
    public void gerarContasRecorrentes() {
        // Transação e lock por pessoa: não se mantém um conjunto inteiro de motoristas bloqueado.
        for (var motorista : motoristas.findAll()) contasPagar.gerarSalarioRecorrente(motorista.getId());
        for (var aluno : alunos.findAllByArquivadoFalseOrderByIdAsc()) contasReceber.gerarRecorrenteSeNecessaria(aluno.getId());
    }
}
