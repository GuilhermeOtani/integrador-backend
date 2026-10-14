package com.example.integrador.Service;

import com.example.integrador.Enum.StatusFinanceiro;
import com.example.integrador.Model.ContaPagar;
import com.example.integrador.Model.Motorista;
import com.example.integrador.Repository.ContaPagarRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import com.example.integrador.Exception.*;
import com.example.integrador.Model.Pessoa;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContaPagarService {
    private final ContaPagarRepository contapagarRepository;
    private final PessoaBloqueioService bloqueios;
    private final EntityManager em;

    public ContaPagarService(ContaPagarRepository contapagarRepository, PessoaBloqueioService bloqueios, EntityManager em) {
        this.contapagarRepository = contapagarRepository;
        this.bloqueios = bloqueios;
        this.em = em;
    }

    public List<ContaPagar> listarTodasContaPagar() {
        return contapagarRepository.findAll();
    }

    @Transactional
    public ContaPagar salvarContaPagar(ContaPagar contapagar) {
        if (contapagar.getId() != null) return atualizarContaPagar(contapagar.getId(), contapagar);
        Long pessoaId = pessoaId(contapagar);
        if (pessoaId != null) {
            Pessoa pessoa = bloqueios.bloquearPessoa(pessoaId);
            bloqueios.exigirNaoArquivado(pessoa);
            contapagar.setMotorista(pessoa);
        }
        return contapagarRepository.save(contapagar);
    }

    public ContaPagar buscarContaPagarPorId(Long id) {
        return contapagarRepository
                .findById(id).orElseThrow(() -> new RuntimeException("ContaPagar de ID " + id + " não encontrado"));
    }

    public void deletarContaPagarPorId(Long id) {
        contapagarRepository.deleteById(id);
    }

    @Transactional
    public ContaPagar atualizarContaPagar(Long id, ContaPagar contapagar) {
        ContaPagar contapagarSalvo = contapagarRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta a pagar não encontrada."));
        Long anterior = pessoaId(contapagarSalvo);
        Long destino = pessoaId(contapagar);
        bloqueios.bloquearPessoasEmOrdem(Stream.of(anterior, destino).filter(Objects::nonNull).toList());
        Pessoa pessoa = destino == null ? null : bloqueios.bloquearPessoa(destino);
        if (pessoa != null && !Objects.equals(anterior, destino)) bloqueios.exigirNaoArquivado(pessoa);
        em.refresh(contapagarSalvo, LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(pessoaId(contapagarSalvo), anterior))
            throw new ConflitoException("O proprietário da conta mudou. Atualize e tente novamente.");
        BeanUtils.copyProperties(contapagar, contapagarSalvo, "id", "motorista");
        contapagarSalvo.setMotorista(pessoa);
        return contapagarRepository.save(contapagarSalvo);
    }
    private Long pessoaId(ContaPagar conta) {
        if (conta.getMotorista() == null) return null;
        if (conta.getMotorista().getId() == null)
            throw new RegraNegocioException("Informe o ID da pessoa vinculada.");
        return conta.getMotorista().getId();
    }
    public void gerarPrimeiraConta(Motorista motorista) {
        ContaPagar novaConta = new ContaPagar();
        novaConta.setDescricao("Salário - " + motorista.getNome());
        novaConta.setValor(motorista.getSalario());
        novaConta.setDataVencimento(LocalDate.now().plusDays(30));
        novaConta.setStatus(StatusFinanceiro.PENDENTE);
        novaConta.setMotorista(motorista);

        contapagarRepository.save(novaConta);
    }

}
