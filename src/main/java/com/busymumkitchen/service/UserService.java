package com.busymumkitchen.service;

import com.busymumkitchen.exception.ResourceNotFoundException;
import com.busymumkitchen.model.FavoriteItem;
import com.busymumkitchen.model.User;
import com.busymumkitchen.repository.FavoriteItemRepository;
import com.busymumkitchen.repository.MenuItemRepository;
import com.busymumkitchen.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FavoriteItemRepository favoriteItemRepository;
    private final MenuItemRepository menuItemRepository;

    public User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public User updateProfile(UUID userId, String fullName, String email, String whatsappNumber) {
        User user = getUserById(userId);
        if (fullName != null) user.setFullName(fullName);
        if (email != null) user.setEmail(email);
        if (whatsappNumber != null) user.setWhatsappNumber(whatsappNumber);
        return userRepository.save(user);
    }

    public List<UUID> getFavoriteMenuItemIds(UUID userId) {
        return favoriteItemRepository.findMenuItemIdsByUserId(userId);
    }

    @Transactional
    public void addFavorite(UUID userId, UUID menuItemId) {
        if (!menuItemRepository.existsById(menuItemId)) {
            throw new ResourceNotFoundException("Menu item not found");
        }
        if (favoriteItemRepository.existsByUserIdAndMenuItemId(userId, menuItemId)) {
            return; // Already a favorite
        }
        User user = getUserById(userId);
        FavoriteItem favorite = FavoriteItem.builder()
                .user(user)
                .menuItemId(menuItemId)
                .build();
        favoriteItemRepository.save(favorite);
    }

    @Transactional
    public void removeFavorite(UUID userId, UUID menuItemId) {
        favoriteItemRepository.deleteByUserIdAndMenuItemId(userId, menuItemId);
    }

    @Transactional
    public void toggleUserStatus(UUID userId, boolean active) {
        User user = getUserById(userId);
        user.setIsActive(active);
        userRepository.save(user);
    }
}
