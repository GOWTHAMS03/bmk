package com.busymumkitchen.controller;

import com.busymumkitchen.dto.common.ApiResponse;
import com.busymumkitchen.model.Address;
import com.busymumkitchen.model.User;
import com.busymumkitchen.repository.AddressRepository;
import com.busymumkitchen.security.SecurityUtils;
import com.busymumkitchen.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SecurityUtils securityUtils;
    private final AddressRepository addressRepository;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProfile() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "id", user.getId(),
                "phoneNumber", user.getPhoneNumber(),
                "fullName", user.getFullName() != null ? user.getFullName() : "",
                "email", user.getEmail() != null ? user.getEmail() : "",
                "whatsappNumber", user.getWhatsappNumber() != null ? user.getWhatsappNumber() : "",
                "role", user.getRole().name(),
                "profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : ""
        )));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<Void>> updateProfile(@RequestBody Map<String, String> body) {
        UUID userId = securityUtils.getCurrentUserId();
        userService.updateProfile(userId,
                body.get("fullName"),
                body.get("email"),
                body.get("whatsappNumber"));
        return ResponseEntity.ok(ApiResponse.success("Profile updated"));
    }

    @GetMapping("/addresses")
    public ResponseEntity<ApiResponse<List<Address>>> getAddresses() {
        UUID userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                addressRepository.findByUserIdOrderByIsDefaultDesc(userId)));
    }

    @PostMapping("/addresses")
    public ResponseEntity<ApiResponse<Address>> addAddress(@RequestBody Address address) {
        User user = securityUtils.getCurrentUser();
        address.setUser(user);
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            addressRepository.clearDefaultForUser(user.getId());
        }
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Address added", addressRepository.save(address)));
    }

    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(@PathVariable UUID addressId) {
        addressRepository.deleteById(addressId);
        return ResponseEntity.ok(ApiResponse.success("Address deleted"));
    }

    @GetMapping("/favorites")
    public ResponseEntity<ApiResponse<List<UUID>>> getFavorites() {
        UUID userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(userService.getFavoriteMenuItemIds(userId)));
    }

    @PostMapping("/favorites/{menuItemId}")
    public ResponseEntity<ApiResponse<Void>> addFavorite(@PathVariable UUID menuItemId) {
        UUID userId = securityUtils.getCurrentUserId();
        userService.addFavorite(userId, menuItemId);
        return ResponseEntity.ok(ApiResponse.success("Added to favorites"));
    }

    @DeleteMapping("/favorites/{menuItemId}")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(@PathVariable UUID menuItemId) {
        UUID userId = securityUtils.getCurrentUserId();
        userService.removeFavorite(userId, menuItemId);
        return ResponseEntity.ok(ApiResponse.success("Removed from favorites"));
    }
}
