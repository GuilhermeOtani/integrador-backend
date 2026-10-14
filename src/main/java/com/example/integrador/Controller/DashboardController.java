package com.example.integrador.Controller;

import com.example.integrador.DTO.DashboardResponse.AlunoDashboard;
import com.example.integrador.DTO.DashboardResponse.MotoristaDashboard;
import com.example.integrador.DTO.DashboardResponse.AdminDashboard;
import com.example.integrador.Service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping("/aluno")
    public AlunoDashboard aluno() { return service.aluno(); }

    @GetMapping("/motorista")
    public MotoristaDashboard motorista() { return service.motorista(); }

    @GetMapping("/admin")
    public AdminDashboard admin(@RequestParam(required = false) String mes) { return service.admin(mes); }
}
