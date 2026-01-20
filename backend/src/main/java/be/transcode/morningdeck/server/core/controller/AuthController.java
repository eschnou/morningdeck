package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.core.dto.AuthResponse;
import be.transcode.morningdeck.server.core.dto.ResendVerificationRequest;
import be.transcode.morningdeck.server.core.dto.VerificationResponse;
import be.transcode.morningdeck.server.core.service.AuthenticationService;
import be.transcode.morningdeck.server.core.service.EmailVerificationService;
import be.transcode.morningdeck.server.core.dto.AuthRequest;
import be.transcode.morningdeck.server.core.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.ok(authenticationService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticate(@RequestBody @Valid AuthRequest request) {
        return ResponseEntity.ok(authenticationService.authenticate(request));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<VerificationResponse> verifyEmail(@RequestParam String token) {
        return ResponseEntity.ok(emailVerificationService.verifyEmail(token));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@RequestBody @Valid ResendVerificationRequest request) {
        emailVerificationService.resendVerification(request.getEmail());
        return ResponseEntity.ok().build();
    }
}
