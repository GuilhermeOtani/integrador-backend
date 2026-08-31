package com.example.integrador.Service;

import com.example.integrador.DTO.GradeDiariaDTO;
import com.example.integrador.DTO.ViagemDTO;
import com.example.integrador.Model.GradeDiaria;
import com.example.integrador.Model.Viagem;
import com.example.integrador.Repository.GradeDiariaRepository;
import com.example.integrador.Repository.RotaRepository;
import com.example.integrador.Repository.OnibusRepository;
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
    private final OnibusRepository onibusRepository;
    private final MotoristaRepository motoristaRepository;

    public GradeDiariaService(GradeDiariaRepository gradeDiariaRepository,
                              RotaRepository rotaRepository,
                              OnibusRepository onibusRepository,
                              MotoristaRepository motoristaRepository) {
        this.gradeDiariaRepository = gradeDiariaRepository;
        this.rotaRepository = rotaRepository;
        this.onibusRepository = onibusRepository;
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
                    .map(vDto -> converterParaViagem(vDto, grade))
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
            gradeSalva.getViagens().clear(); // Limpa as antigas

            for (ViagemDTO vDto : dtoAtualizada.getViagens()) {
                Viagem viagem = converterParaViagem(vDto, gradeSalva);
                gradeSalva.getViagens().add(viagem);
            }
        }

        GradeDiaria gradeAtualizada = gradeDiariaRepository.save(gradeSalva);
        return new GradeDiariaDTO(gradeAtualizada);
    }

    public void deletarGradePorId(Long id) {
        gradeDiariaRepository.deleteById(id);
    }

    private Viagem converterParaViagem(ViagemDTO dto, GradeDiaria grade) {
        Viagem viagem = new Viagem();
        BeanUtils.copyProperties(dto, viagem, "id");

        if (dto.getId() != null) {
            viagem.setId(dto.getId());
        }

        viagem.setGradeDiaria(grade);

        if (dto.getRotaId() != null) {
            viagem.setRota(rotaRepository.getReferenceById(dto.getRotaId()));
        }
        if (dto.getOnibusId() != null) {
            viagem.setOnibus(onibusRepository.getReferenceById(dto.getOnibusId()));
        }
        if (dto.getMotoristaId() != null) {
            viagem.setMotorista(motoristaRepository.getReferenceById(dto.getMotoristaId()));
        }

        return viagem;
    }
}