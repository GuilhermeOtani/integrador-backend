package com.example.integrador.Service;

import com.example.integrador.DTO.DashboardResponse.*;
import com.example.integrador.DTO.ViagemDashboardBase;
import com.example.integrador.Enum.DiaSemana;
import com.example.integrador.Enum.StatusOnibus;
import com.example.integrador.Model.Aluno;
import com.example.integrador.Model.Motorista;
import com.example.integrador.Repository.DashboardConsultasRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Consulta interna compartilhada. A autorização cabe às fachadas HTTP que a utilizam. */
@Service
@Transactional(readOnly = true)
public class AgendaTransporteService {
    private final DashboardConsultasRepository consultas;

    public AgendaTransporteService(DashboardConsultasRepository consultas) { this.consultas = consultas; }

    public TransporteResumo aluno(Aluno aluno, LocalDate data) {
        Long faculdadeId = aluno.getFaculdade() == null ? null : aluno.getFaculdade().getId();
        return transporte(consultas.viagensAluno(faculdadeId), faculdadeId, data);
    }

    public TransporteResumo motorista(Motorista motorista, LocalDate data) {
        return transporte(consultas.viagensMotorista(motorista.getId()), null, data);
    }

    private TransporteResumo transporte(List<ViagemDashboardBase> viagens, Long faculdadeId, LocalDate hoje) {
        Set<Long> rotaIds = new HashSet<>();
        for (var viagem : viagens) if (viagem.rotaId() != null) rotaIds.add(viagem.rotaId());
        var rotas = consultas.rotas(faculdadeId, rotaIds);
        List<Long> ids = rotas.stream().map(r -> r.getId()).toList();
        Map<Long, Map<Long, PontoResumo>> pontos = new HashMap<>();
        adicionarPontos(pontos, consultas.pontosDaColecao(ids));
        adicionarPontos(pontos, consultas.pontosDaReferencia(ids));
        Comparator<PontoResumo> ordemPontos = Comparator.comparing(PontoResumo::ordemParada,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(PontoResumo::id);
        Map<Long, RotaResumo> mapaRotas = new HashMap<>();
        for (var rota : rotas) {
            List<PontoResumo> lista = pontos.getOrDefault(rota.getId(), Map.of()).values().stream().sorted(ordemPontos).toList();
            mapaRotas.put(rota.getId(), new RotaResumo(rota.getId(), rota.getNome(), rota.getDescricao(), lista));
        }
        Map<Long, List<FaculdadeResumo>> faculdades = new HashMap<>();
        for (Object[] row : consultas.faculdadesDasViagens(viagens.stream().map(ViagemDashboardBase::id).toList())) {
            faculdades.computeIfAbsent((Long) row[0], id -> new ArrayList<>())
                    .add(new FaculdadeResumo((Long) row[1], (String) row[2], (String) row[3]));
        }
        List<ViagemResumo> agenda = new ArrayList<>();
        List<ViagemResumo> semGrade = new ArrayList<>();
        for (var viagem : viagens) {
            LocalDate ocorrencia = ocorrenciaSemanal(hoje, viagem.diaSemana());
            List<String> avisos = new ArrayList<>();
            if (ocorrencia == null) avisos.add("Programação não definida. A administração precisa cadastrar a grade/dia da semana.");
            boolean disponivel = viagem.onibusId() != null && viagem.statusOnibus() == StatusOnibus.OPERANDO;
            if (!disponivel) avisos.add(viagem.onibusId() == null ? "Ônibus não definido."
                    : "Ônibus indisponível ou com estado não informado. Confirme a escala com a administração.");
            if (viagem.motoristaId() == null) avisos.add("Motorista não definido.");
            if (viagem.rotaId() == null) avisos.add("Rota não definida.");
            ViagemResumo resumo = new ViagemResumo(viagem.id(),
                    viagem.gradeId() == null ? null : new GradeResumo(viagem.gradeId(), viagem.diaSemana(), viagem.gradeDescricao()),
                    ocorrencia, mapaRotas.get(viagem.rotaId()),
                    viagem.onibusId() == null ? null : new OnibusResumo(viagem.onibusId(), viagem.numeroIdentificacao(),
                            viagem.placa(), viagem.modelo(), viagem.capacidade(), viagem.statusOnibus()),
                    viagem.motoristaId() == null ? null : new PessoaResumo(viagem.motoristaId(), viagem.motoristaNome()),
                    List.copyOf(faculdades.getOrDefault(viagem.id(), List.of())), disponivel, List.copyOf(avisos));
            if (ocorrencia == null) semGrade.add(resumo); else agenda.add(resumo);
        }
        agenda.sort(Comparator.comparing(ViagemResumo::dataOcorrencia).thenComparing(ViagemResumo::id));
        semGrade.sort(Comparator.comparing(ViagemResumo::id));
        var rotasOrdenadas = mapaRotas.values().stream().sorted(Comparator.comparing(RotaResumo::nome,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).thenComparing(RotaResumo::id)).toList();
        return new TransporteResumo(agenda.stream().filter(v -> hoje.equals(v.dataOcorrencia())).toList(),
                List.copyOf(agenda), List.copyOf(semGrade), rotasOrdenadas);
    }

    private void adicionarPontos(Map<Long, Map<Long, PontoResumo>> pontos, List<Object[]> rows) {
        for (Object[] row : rows) {
            var ponto = new PontoResumo((Long) row[1], (String) row[2], (Integer) row[3]);
            pontos.computeIfAbsent((Long) row[0], id -> new HashMap<>()).put(ponto.id(), ponto);
        }
    }

    private LocalDate ocorrenciaSemanal(LocalDate hoje, DiaSemana dia) {
        if (dia == null) return null;
        DayOfWeek diaDaGrade = switch (dia) {
            case SEGUNDA -> DayOfWeek.MONDAY;
            case TERCA -> DayOfWeek.TUESDAY;
            case QUARTA -> DayOfWeek.WEDNESDAY;
            case QUINTA -> DayOfWeek.THURSDAY;
            case SEXTA -> DayOfWeek.FRIDAY;
        };
        int deslocamento = Math.floorMod(diaDaGrade.getValue() - hoje.getDayOfWeek().getValue(), 7);
        return hoje.plusDays(deslocamento);
    }

}

