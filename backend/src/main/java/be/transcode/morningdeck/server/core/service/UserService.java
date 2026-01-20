package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.config.AppConfig;
import be.transcode.morningdeck.server.core.dto.*;
import be.transcode.morningdeck.server.core.exception.AccessDeniedException;
import be.transcode.morningdeck.server.core.exception.BadRequestException;
import be.transcode.morningdeck.server.core.exception.ResourceNotFoundException;
import be.transcode.morningdeck.server.core.exception.UserAlreadyExistsException;
import be.transcode.morningdeck.server.core.model.InviteCode;
import be.transcode.morningdeck.server.core.model.Language;
import be.transcode.morningdeck.server.core.model.Subscription;
import be.transcode.morningdeck.server.core.model.User;
import be.transcode.morningdeck.server.provider.analytics.AnalyticsService;
import be.transcode.morningdeck.server.provider.emailsend.EmailService;
import be.transcode.morningdeck.server.provider.storage.StorageProvider;
import be.transcode.morningdeck.server.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private static final String REGISTER = "REGISTER";
    private static final String CHANGE_PASSWORD = "CHANGE_PASSWORD";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageProvider storageProvider;
    private final SubscriptionService subscriptionService;
    private final AnalyticsService analyticsService;
    private final EmailService emailService;
    private final AppConfig appConfig;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(normalizeUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!user.isEnabled()) {
            throw new DisabledException("User account is disabled");
        }

        if (!user.isEmailVerified()) {
            throw new DisabledException("Email not verified. Please check your inbox.");
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    // Internal API - Returns entities for service-to-service communication
    public User getInternalUserByUsername(String username) {
        return userRepository.findByUsername(normalizeUsername(username))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    public User getInternalUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    @Transactional
    public UserProfileDTO createUser(String username, String name, String email, String password, Language language) {
        return createUser(username, name, email, password, language, true, null);
    }

    @Transactional
    public UserProfileDTO createUser(String username, String name, String email, String password, Language language, boolean sendWelcomeEmail) {
        return createUser(username, name, email, password, language, sendWelcomeEmail, null);
    }

    @Transactional
    public UserProfileDTO createUser(String username, String name, String email, String password, Language language, boolean sendWelcomeEmail, InviteCode inviteCode) {
        // Normalize to avoid duplicate with space/uppercase
        String normalizedUsername = normalizeUsername(username);
        String normalizedEmail = normalizeEmail(email);
        String normalizedName = normalizeName(name);

        // Check if username or email already exists
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new UserAlreadyExistsException("Username already exists");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException("Email already exists");
        }

        var user = User.builder()
                .username(normalizedUsername)
                .name(normalizedName)
                .email(normalizedEmail)
                .language(language != null ? language : Language.ENGLISH)
                .password(passwordEncoder.encode(password))
                .inviteCode(inviteCode)
                .build();

        // Create free subscription
        Subscription subscription = subscriptionService.createSubscription(user, Subscription.SubscriptionPlan.FREE, false);
        user.setSubscription(subscription);

        // Send welcome email (only if not using email verification)
        if (sendWelcomeEmail) {
            emailService.sendWelcomeEmail(normalizedEmail, normalizedName, appConfig.getRootDomain());
        }

        // Save the user
        user = userRepository.save(user);

        // Track analytic
        analyticsService.logEvent(REGISTER, user.getId().toString());

        return mapToDTO(user);
    }

    @Transactional
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getUserById(UUID id) {
        return userRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getUserByUsername(String username) {
        return userRepository.findByUsername(normalizeUsername(username))
                .map(this::mapToDTO)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public PublicUserProfileDTO getPublicProfile(String id) {
        User user = userRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return PublicUserProfileDTO.builder()
                .id(user.getId().toString())
                .username(user.getUsername())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getCurrentUserProfile(String username) {
        User user = getInternalUserByUsername(normalizeUsername(username));
        return mapToDTO(user);
    }

    @Transactional
    public UserProfileDTO updateProfile(String username, UpdateUserProfileDTO updateDto) {
        String normalizedUsername = normalizeUsername(username);
        User user = getInternalUserByUsername(normalizedUsername);

        if (updateDto.getEmail() != null && !updateDto.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(updateDto.getEmail())) {
                throw new UserAlreadyExistsException("Email already exists");
            }
            user.setEmail(updateDto.getEmail());
        }

        if (updateDto.getName() != null) {
            user.setName(updateDto.getName());
        }

        if (updateDto.getLanguage() != null) {
            user.setLanguage(updateDto.getLanguage());
        }

        user = userRepository.save(user);
        return getCurrentUserProfile(normalizedUsername);
    }

    @Transactional
    public AvatarResponseDTO uploadAvatar(String username, MultipartFile avatar) {
        if (avatar == null || avatar.isEmpty()) {
            throw new BadRequestException("Avatar file cannot be empty");
        }

        User user = getInternalUserByUsername(normalizeUsername(username));

        try {
            storageProvider.store(user.getId(), avatar.getBytes(), avatar.getContentType());

            // Update the avatar URL in the user entity
            String avatarUrl = buildAvatarUrl(user.getId());
            user.setAvatarUrl(avatarUrl);
            userRepository.save(user);

            return AvatarResponseDTO.builder()
                    .avatarUrl(avatarUrl)
                    .build();
        } catch (IOException e) {
            throw new BadRequestException("Failed to process avatar file: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteAvatar(String username) {
        User user = getInternalUserByUsername(username);

        if (user.getAvatarUrl() == null) {
            throw new ResourceNotFoundException("User does not have an avatar");
        }

        storageProvider.delete(user.getId());

        // Clear the avatar URL in the user entity
        user.setAvatarUrl(null);
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordDTO passwordDto) {
        User user = getInternalUserByUsername(username);

        if (!passwordEncoder.matches(passwordDto.getCurrentPassword(), user.getPassword())) {
            throw new AccessDeniedException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(passwordDto.getNewPassword()));
        userRepository.save(user);

        // Track analytic
        analyticsService.logEvent(CHANGE_PASSWORD, user.getId().toString());
    }

    private UserProfileDTO mapToDTO(User user) {
        return UserProfileDTO.builder()
                .id(user.getId().toString())
                .username(user.getUsername())
                .name(user.getName())
                .email(user.getEmail())
                .language(user.getLanguage())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .build();
    }

    private String buildAvatarUrl(UUID userId) {
        return String.format("%s/public/avatars/%s", appConfig.getRootDomain(), userId);
    }

    public String normalizeUsername(String username) {
        if (username == null) return null;
        return username.toLowerCase().trim();
    }

    public String normalizeEmail(String email) {
        if (email == null) return null;
        return email.toLowerCase().trim();
    }

    public String normalizeName(String name) {
        if (name == null) return null;
        return name.trim();
    }
}
