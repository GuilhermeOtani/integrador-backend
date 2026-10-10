package com.example.integrador.Controller;

import com.example.integrador.DTO.*;
import com.example.integrador.Enum.NivelUrgencia;
import com.example.integrador.Enum.StatusChamado;
import com.example.integrador.Service.ChamadoManutencaoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/chamados-manutencao")
public class ChamadoManutencaoController {
    private final ChamadoManutencaoService service;

    public ChamadoManutencaoController(ChamadoManutencaoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ChamadoManutencaoResponse> abrir(@Valid @RequestBody AbrirChamadoRequest request) {
        var response = service.abrir(request);
        return ResponseEntity.created(URI.create("/chamados-manutencao/" + response.id())).body(response);
    }

    @GetMapping("/minhas")
    public List<ChamadoManutencaoResponse> minhas() { return service.minhas(); }

    @GetMapping("/onibus")
    public List<OnibusChamadoResponse> onibus() { return service.listarOnibus(); }

    @GetMapping
    public List<ChamadoManutencaoResponse> listar(@RequestParam(required = false) StatusChamado status,
            @RequestParam(required = false) NivelUrgencia urgencia,
            @RequestParam(required = false) Long onibusId) {
        return service.listar(status, urgencia, onibusId);
    }

    @GetMapping("/{id}")
    public ChamadoManutencaoResponse buscar(@PathVariable Long id) { return service.buscar(id); }

    @PutMapping("/{id}/atendimento")
    public ChamadoManutencaoResponse atender(@PathVariable Long id,
            @Valid @RequestBody AtenderChamadoRequest request) {
        return service.atender(id, request);
    }
}
