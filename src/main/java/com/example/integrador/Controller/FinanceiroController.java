package com.example.integrador.Controller;

import com.example.integrador.DTO.SaldoBaseDTO;
import com.example.integrador.Service.SaldoBaseCaixaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/financeiro/saldo-base")
public class FinanceiroController {
    private final SaldoBaseCaixaService service;
    public FinanceiroController(SaldoBaseCaixaService service) { this.service = service; }
    @GetMapping public SaldoBaseDTO buscar() { return service.buscar(); }
    @PutMapping public SaldoBaseDTO salvar(@Valid @RequestBody SaldoBaseDTO entrada) { return service.salvar(entrada); }
}
