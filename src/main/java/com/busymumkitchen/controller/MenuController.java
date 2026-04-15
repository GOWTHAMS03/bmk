package com.busymumkitchen.controller;

import com.busymumkitchen.dto.common.ApiResponse;
import com.busymumkitchen.dto.common.PagedResponse;
import com.busymumkitchen.dto.menu.CategoryResponse;
import com.busymumkitchen.dto.menu.MenuItemResponse;
import com.busymumkitchen.model.User;
import com.busymumkitchen.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/restaurants/{restaurantId}/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories(
            @PathVariable("restaurantId") UUID restaurantId) {
        return ResponseEntity.ok(ApiResponse.success(menuService.getCategories()));
    }

        @GetMapping("/restaurants/{restaurantId}/menu")
        public ResponseEntity<ApiResponse<PagedResponse<MenuItemResponse>>> getMenuItems(
            @PathVariable("restaurantId") UUID restaurantId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        UUID userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            userId = user.getId();
        }

        return ResponseEntity.ok(ApiResponse.success(
                menuService.getMenuItems(categoryId, search, page, size, userId)));
    }

    @GetMapping("/menu-items/{itemId}")
    public ResponseEntity<ApiResponse<MenuItemResponse>> getMenuItem(
            @PathVariable UUID itemId,
            Authentication authentication) {

        UUID userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            userId = user.getId();
        }

        return ResponseEntity.ok(ApiResponse.success(menuService.getMenuItemById(itemId, userId)));
    }
}
