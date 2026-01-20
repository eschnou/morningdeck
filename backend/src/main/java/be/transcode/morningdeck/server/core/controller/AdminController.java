package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.core.dto.*;
import be.transcode.morningdeck.server.core.service.AdminService;
import be.transcode.morningdeck.server.core.service.EmailVerificationService;
import be.transcode.morningdeck.server.core.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;
    private final EmailVerificationService emailVerificationService;

    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserListItemDTO>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(adminService.listUsers(search, enabled, pageable));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserDetailDTO> getUserDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getUserDetail(id));
    }

    @PutMapping("/users/{id}/enabled")
    public ResponseEntity<AdminUserDetailDTO> updateEnabled(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody AdminUpdateEnabledDTO dto) {
        UUID adminId = getAdminId(userDetails);
        return ResponseEntity.ok(adminService.updateUserEnabled(adminId, id, dto.getEnabled()));
    }

    @PutMapping("/users/{id}/password")
    public ResponseEntity<Void> resetPassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody AdminResetPasswordDTO dto) {
        UUID adminId = getAdminId(userDetails);
        adminService.resetPassword(adminId, id, dto.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/{id}/email")
    public ResponseEntity<Void> updateEmail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody AdminUpdateEmailDTO dto) {
        UUID adminId = getAdminId(userDetails);
        adminService.updateEmail(adminId, id, dto.getEmail());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/{id}/subscription")
    public ResponseEntity<Void> updateSubscription(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody AdminUpdateSubscriptionDTO dto) {
        UUID adminId = getAdminId(userDetails);
        adminService.updateSubscription(adminId, id, dto.getPlan());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/{id}/credits")
    public ResponseEntity<Void> adjustCredits(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody AdminAdjustCreditsDTO dto) {
        UUID adminId = getAdminId(userDetails);
        adminService.adjustCredits(adminId, id, dto.getAmount(), dto.getMode());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/{id}/verify-email")
    public ResponseEntity<Void> verifyUserEmail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        UUID adminId = getAdminId(userDetails);
        emailVerificationService.adminVerifyEmail(adminId, id);
        return ResponseEntity.ok().build();
    }

    private UUID getAdminId(UserDetails userDetails) {
        return userService.getInternalUserByUsername(userDetails.getUsername()).getId();
    }
}
