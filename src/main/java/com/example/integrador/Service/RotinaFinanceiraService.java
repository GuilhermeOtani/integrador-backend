package com.example.integrador.Service;

import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Model.ContaPagar;
import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.Motorista;
import com.example.integrador.Repository.AlunoRepository;
import com.example.integrador.Repository.ContaPagarRepository;
import com.example.integrador.Repository.MotoristaRepository;
import jakarta.transaction.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class RotinaFinanceiraService {

    private final MotoristaRepository motoristaRepository;
    private final ContaPagarRepository contaPagarRepository;
    private final AlunoRepository alunoRepository;
    private final ContaReceberService contaReceberService;

    public RotinaFinanceiraService(MotoristaRepository motoristaRepository,
                                   ContaPagarRepository contaPagarRepository,
                                   AlunoRepository alunoRepository,
                                   ContaReceberService contaReceberService) {
        this.motoristaRepository = motoristaRepository;
        this.contaPagarRepository = contaPagarRepository;
        this.alunoRepository = alunoRepository;
        this.contaReceberService = contaReceberService;
    }

    @Transactional
    @Scheduled(cron = "0 0 2 * * *")
    public void gerarContasRecorrentes() {

        List<Motorista> motoristas = motoristaRepository.findAll();

        for (Motorista motorista : motoristas) {
            ContaPagar ultimaConta = contaPagarRepository.findTopByMotoristaIdOrderByDataVencimentoDesc(motorista.getId());

            if (ultimaConta != null) {
                if (!ultimaConta.getDataVencimento().isAfter(LocalDate.now())) {

                    ContaPagar novaConta = new ContaPagar();
                    novaConta.setDescricao("Salário - " + motorista.getNome());
                    novaConta.setValor(motorista.getSalario());

                    novaConta.setDataVencimento(ultimaConta.getDataVencimento().plusDays(30));
                    novaConta.setStatus(StatusFinanceiro.PENDENTE);
                    novaConta.setMotorista(motorista);

                    contaPagarRepository.save(novaConta);
                }
            }
        }

        gerarMatriculasRecorrentes();
    }

    private void gerarMatriculasRecorrentes() {
        // Ordem estável dos locks. O serviço revalida cada aluno e a última conta sob bloqueio.
        for (Aluno aluno : alunoRepository.findAllByArquivadoFalseOrderByIdAsc())
            contaReceberService.gerarRecorrenteSeNecessaria(aluno.getId());
    }
}
