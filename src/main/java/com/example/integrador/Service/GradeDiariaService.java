package com.example.integrador.Service;

import com.example.integrador.DTO.GradeDiariaDTO;
import com.example.integrador.DTO.ViagemDTO;
import com.example.integrador.Model.GradeDiaria;
import com.example.integrador.Model.Viagem;
import com.example.integrador.Repository.GradeDiariaRepository;
import com.example.integrador.Repository.RotaRepository;
import com.example.integrador.Exception.RegraNegocioException;
import com.example.integrador.Repository.MotoristaRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GradeDiariaService {

    private final GradeDiariaRepository gradeDiariaRepository;
    private final RotaRepository rotaRepository;
    private final DisponibilidadeOnibusService disponibilidadeOnibus;
    private final MotoristaRepository motoristaRepository;

    public GradeDiariaService(GradeDiariaRepository gradeDiariaRepository,
                              RotaRepository rotaRepository,
                              DisponibilidadeOnibusService disponibilidadeOnibus,
                              MotoristaRepository motoristaRepository) {
        this.gradeDiariaRepository = gradeDiariaRepository;
        this.rotaRepository = rotaRepository;
        this.disponibilidadeOnibus = disponibilidadeOnibus;
        this.motoristaRepository = motoristaRepository;
    }

    @Transactional(readOnly = true)
    public List<GradeDiariaDTO> listarTodasGrades() {
        return gradeDiariaRepository.findAll()
                .stream()
                .map(GradeDiariaDTO::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public GradeDiariaDTO buscarGradePorId(Long id) {
        GradeDiaria grade = gradeDiariaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Grade Diária de ID " + id + " não encontrada"));
        return new GradeDiariaDTO(grade);
    }

    @Transactional
    public GradeDiariaDTO salvarGrade(GradeDiariaDTO dto) {
        GradeDiaria grade = new GradeDiaria();
        BeanUtils.copyProperties(dto, grade, "id", "viagens");

        if (dto.getViagens() != null && !dto.getViagens().isEmpty()) {
            List<Viagem> viagensEntity = dto.getViagens().stream()
                    .map(vDto -> converterParaViagem(vDto, grade, null))
                    .collect(Collectors.toList());
            grade.setViagens(viagensEntity);
        }

        GradeDiaria gradeSalva = gradeDiariaRepository.save(grade);
        return new GradeDiariaDTO(gradeSalva);
    }

    @Transactional
    public GradeDiariaDTO atualizarGrade(Long id, GradeDiariaDTO dtoAtualizada) {
        GradeDiaria gradeSalva = gradeDiariaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Grade Diária de ID " + id + " não encontrada"));

        BeanUtils.copyProperties(dtoAtualizada, gradeSalva, "id", "viagens");

        if (dtoAtualizada.getViagens() != null) {
            var existentes = gradeSalva.getViagens().stream().collect(Collectors.toMap(Viagem::getId, v -> v));
            var idsRecebidos = new java.util.HashSet<Long>();
            var atualizadas = new java.util.ArrayList<Viagem>();
            for (ViagemDTO vDto : dtoAtualizada.getViagens()) {
                Viagem existente = vDto.getId() == null ? null : existentes.get(vDto.getId());
                if (vDto.getId() != null && (existente == null || !idsRecebidos.add(vDto.getId()))) {
                    throw new RegraNegocioException("A viagem deve pertencer à grade e não pode ser repetida");
                }
                atualizadas.add(converterParaViagem(vDto, gradeSalva, existente));
            }
            gradeSalva.getViagens().clear();
            gradeSalva.getViagens().addAll(atualizadas);
        }

        GradeDiaria gradeAtualizada = gradeDiariaRepository.save(gradeSalva);
        return new GradeDiariaDTO(gradeAtualizada);
    }

    public void deletarGradePorId(Long id) {
        gradeDiariaRepository.deleteById(id);
    }

    private Viagem converterParaViagem(ViagemDTO dto, GradeDiaria grade, Viagem existente) {
        if (existente == null && dto.getId() != null) {
            throw new RegraNegocioException("Uma nova viagem não deve informar id");
        }
        Viagem viagem = existente == null ? new Viagem() : existente;
        BeanUtils.copyProperties(dto, viagem, "id");

        viagem.setGradeDiaria(grade);

        if (dto.getRotaId() != null) {
            viagem.setRota(rotaRepository.getReferenceById(dto.getRotaId()));
        } else {
            viagem.setRota(null);
        }
        Long anteriorId = viagem.getOnibus() == null ? null : viagem.getOnibus().getId();
        viagem.setOnibus(disponibilidadeOnibus.resolverAtribuicao(dto.getOnibusId(), anteriorId));
        if (dto.getMotoristaId() != null) {
            viagem.setMotorista(motoristaRepository.getReferenceById(dto.getMotoristaId()));
        } else {
            viagem.setMotorista(null);
        }

        return viagem;
    }
}
