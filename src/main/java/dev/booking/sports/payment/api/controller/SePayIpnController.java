package dev.booking.sports.payment.api.controller;

import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import dev.booking.sports.payment.application.SePayIpnService;
import dev.booking.sports.payment.api.dto.response.SePayIpnResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/v1/payments/sepay")
@Tag(name = "SePay IPN")
@RequiredArgsConstructor
public class SePayIpnController {

    private final SePayIpnService service;

    @PostMapping(value = "/ipn", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Receive a SePay Gateway IPN")
    public SePayIpnResponse receiveIpn(
            @RequestHeader(value = "X-Secret-Key", required = false) String secretKey,
            @RequestBody String payload) {
        service.receive(secretKey, payload);
        return new SePayIpnResponse(true);
    }
}
