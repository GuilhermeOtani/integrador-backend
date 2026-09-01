package com.example.integrador.Service;

import com.example.integrador.DTO.ViagemDTO;
import com.example.integrador.Enum.StatusMatricula;
import com.example.integrador.Model.Faculdade;
import com.example.integrador.Model.Viagem;
import com.example.integrador.Repository.AlunoRepository;
import com.example.integrador.Repository.ViagemRepository;
import com.example.integrador.Repository.RotaRepository;
import com.example.integrador.Repository.OnibusRepository;
import com.example.integrador.Repository.MotoristaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ViagemService {

    private final ViagemRepository viagemRepository;
    private final AlunoRepository alunoRepository;
    private final RotaRepository rotaRepository;
    private final OnibusRepository onibusRepository;
    private final MotoristaRepository motoristaRepository;

    public ViagemService(ViagemRepository viagemRepository,
                         AlunoRepository alunoRepository,
                         RotaRepository rotaRepository,
                         OnibusRepository onibusRepository,
                         MotoristaRepository motoristaRepository) {
        this.viagemRepository = viagemRepository;
        this.alunoRepository = alunoRepository;
        this.rotaRepository = rotaRepository;
        this.onibusRepository = onibusRepository;
        this.motoristaRepository = motoristaRepository;
    }

    @Transactional(readOnly = true)
    public List<ViagemDTO> listarTodasViagens() {
        return viagemRepository.findAll()
                .stream()
                .map(ViagemDTO::new)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MOTORISTA')")
    public List<ViagemDTO> listarMinhasViagens(Long pessoaId) {
        return viagemRepository.findAllByMotoristaId(pessoaId).stream()
                .map(ViagemDTO::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public ViagemDTO buscarViagemPorId(Long id) {
        Viagem viagem = viagemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Viagem de ID " + id + " não encontrada"));
        return new ViagemDTO(viagem);
    }

    @Transactional
    public ViagemDTO salvarViagem(ViagemDTO dto) {
        Viagem viagem = new Viagem();
        BeanUtils.copyProperties(dto, viagem, "id");

        mapearRelacionamentos(dto, viagem);
        validarCapacidadeOnibus(viagem);

        Viagem viagemSalva = viagemRepository.save(viagem);

        // Conversão feita DENTRO da transação ativa!
        return new ViagemDTO(viagemSalva);
    }

    @Transactional
    public ViagemDTO atualizarViagem(Long id, ViagemDTO dto) {
        // Busca a entidade internamente
        Viagem viagemSalva = viagemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Viagem de ID " + id + " não encontrada"));

        BeanUtils.copyProperties(dto, viagemSalva, "id");

        mapearRelacionamentos(dto, viagemSalva);
        validarCapacidadeOnibus(viagemSalva);

        Viagem viagemAtualizada = viagemRepository.save(viagemSalva);

        // Conversão feita DENTRO da transação ativa!
        return new ViagemDTO(viagemAtualizada);
    }

    public void deletarViagemPorId(Long id) {
        viagemRepository.deleteById(id);
    }

    private void mapearRelacionamentos(ViagemDTO dto, Viagem viagem) {
        if (dto.getRotaId() != null) {
            viagem.setRota(rotaRepository.getReferenceById(dto.getRotaId()));
        } else {
            viagem.setRota(null);
        }

        if (dto.getOnibusId() != null) {
            viagem.setOnibus(onibusRepository.getReferenceById(dto.getOnibusId()));
        } else {
            viagem.setOnibus(null);
        }

        if (dto.getMotoristaId() != null) {
            viagem.setMotorista(motoristaRepository.getReferenceById(dto.getMotoristaId()));
        } else {
            viagem.setMotorista(null);
        }
    }

    private void validarCapacidadeOnibus(Viagem viagem) {
        if (viagem.getOnibus() != null && viagem.getFaculdades() != null && !viagem.getFaculdades().isEmpty()) {

            int capacidadeMax = viagem.getOnibus().getCapacidade();
            long totalAlunosPrevistos = 0;

            for (Faculdade faculdade : viagem.getFaculdades()) {
                totalAlunosPrevistos += alunoRepository.countByFaculdadeIdAndStatusMatricula(faculdade.getId(), StatusMatricula.ATIV0);
            }

            if (totalAlunosPrevistos > capacidadeMax) {
                throw new RuntimeException("Não é possível salvar/atualizar a viagem! O total de alunos ativos (" + totalAlunosPrevistos + ") excede a capacidade do ônibus (" + capacidadeMax + " lugares).");
            }
        }
    }
}
