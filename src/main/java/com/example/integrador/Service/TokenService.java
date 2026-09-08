package com.example.integrador.Service;

import com.example.integrador.Model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class TokenService {
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration expiration;

    public TokenService(JwtEncoder jwtEncoder,
                        @Value("${app.security.jwt.issuer}") String issuer,
                        @Value("${app.security.jwt.expiration}") Duration expiration) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.expiration = expiration;
    }

    public TokenEmitido emitir(Usuario usuario) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(agora)
                .expiresAt(agora.plus(expiration))
                .subject(usuario.getId().toString())
                .claim("email", usuario.getEmail())
                .claim("pessoaId", usuario.getPessoa().getId())
                .claim("roles", List.of(usuario.getTipoPessoa().name()))
                .claim("ver", usuario.getTokenVersion())
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new TokenEmitido(token, expiration.toSeconds());
    }

    public record TokenEmitido(String valor, long expiresIn) {
    }
}
