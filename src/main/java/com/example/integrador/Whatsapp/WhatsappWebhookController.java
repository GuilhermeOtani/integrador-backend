package com.example.integrador.Whatsapp;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;

@RestController
@RequestMapping("/webhooks/whatsapp")
public class WhatsappWebhookController {
    private final WhatsappWebhookService webhook;
    public WhatsappWebhookController(WhatsappWebhookService webhook) { this.webhook = webhook; }
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String verificar(@RequestParam("hub.mode") String mode, @RequestParam("hub.verify_token") String token,
                            @RequestParam("hub.challenge") String challenge) {
        return webhook.verificar(mode, token, challenge);
    }
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receber(HttpServletRequest request,
                                        @RequestHeader(value = "X-Hub-Signature-256", required = false) String assinatura) throws IOException {
        if (request.getContentLengthLong() > 2_000_000) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook excede o limite");
        }
        byte[] corpo = request.getInputStream().readNBytes(2_000_001);
        webhook.receber(corpo, assinatura); return ResponseEntity.ok().build();
    }
}
