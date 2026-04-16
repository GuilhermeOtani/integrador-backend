package com.example.integrador.Controller;

import com.example.integrador.DTO.MotoristaDTO;
import com.example.integrador.Model.Motorista;
import com.example.integrador.Service.MotoristaService;
import jakarta.transaction.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/motorista")
@CrossOrigin(origins = "*")
@Transactional
public class MotoristaController {

    private final MotoristaService motoristaService;

    public MotoristaController(MotoristaService motoristaService) {
        this.motoristaService = motoristaService;
    }

    @GetMapping("/listar")
    public List<MotoristaDTO> listarTodosMotoristas() {
        return motoristaService.listarTodosMotoristas()
                .stream()
                .map(MotoristaDTO::new)
                .toList();
    }

    @PostMapping("/salvar-motorista")
    public MotoristaDTO salvarMotorista(@RequestBody MotoristaDTO dto) {
        Motorista motoristaSalvo = motoristaService.salvarMotorista(dto);
        return new MotoristaDTO(motoristaSalvo); // Retorna apenas o DTO
    }

    @GetMapping("/buscar-motorista/{id}")
    public MotoristaDTO buscarMotoristaPorId(@PathVariable Long id) {
        Motorista motorista = motoristaService.buscarMotoristaPorId(id);
        return new MotoristaDTO(motorista);
    }

    @DeleteMapping("/deletar-motorista/{id}")
    public void deletarMotoristaPorId(@PathVariable Long id) {
        motoristaService.deletarMotoristaPorId(id);
    }

    @PutMapping("/atualizar-motorista/{id}")
    public MotoristaDTO atualizarMotoristaPorId(@PathVariable Long id, @RequestBody MotoristaDTO dto) {
        Motorista motoristaAtualizado = motoristaService.atualizarMotorista(id, dto);
        return new MotoristaDTO(motoristaAtualizado);
    }
}
