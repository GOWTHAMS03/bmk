package com.busymumkitchen.service;

import com.busymumkitchen.dto.menu.*;
import com.busymumkitchen.dto.common.PagedResponse;
import com.busymumkitchen.exception.BadRequestException;
import com.busymumkitchen.exception.ResourceNotFoundException;
import com.busymumkitchen.model.Category;
import com.busymumkitchen.model.MenuItem;
import com.busymumkitchen.repository.CategoryRepository;
import com.busymumkitchen.repository.FavoriteItemRepository;
import com.busymumkitchen.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuService {

    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;
    // restaurantRepository removed; we track restaurant by UUID only
    private final FavoriteItemRepository favoriteItemRepository;

    // ==================== CATEGORIES ====================

    @Cacheable(value = "categories")
    public List<CategoryResponse> getCategories() {
        List<Category> categories = categoryRepository.findByIsActiveTrueOrderBySortOrderAsc();

        return categories.stream()
                .map(this::toCategoryResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw new BadRequestException("Category with this name already exists");
        }

        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .sortOrder(request.getSortOrder())
                .isActive(request.getIsActive())
                .build();

        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse updateCategory(UUID categoryId, CategoryRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setImageUrl(request.getImageUrl());
        category.setSortOrder(request.getSortOrder());
        category.setIsActive(request.getIsActive());

        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    @CacheEvict(value = {"categories", "menu"}, allEntries = true)
    public void deleteCategory(UUID categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found");
        }
        categoryRepository.deleteById(categoryId);
    }

    // ==================== MENU ITEMS ====================

    @Cacheable(value = "menu", key = "#categoryId + '-' + #search + '-' + #page + '-' + #size")
    public PagedResponse<MenuItemResponse> getMenuItems(UUID categoryId,
                                                         String search, int page, int size,
                                                         UUID currentUserId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MenuItem> itemPage;

        if (search != null && !search.isBlank()) {
            itemPage = menuItemRepository.searchAll(search, pageable);
        } else if (categoryId != null) {
            itemPage = menuItemRepository.findByCategoryIdAndIsAvailableTrue(categoryId, pageable);
        } else {
            itemPage = menuItemRepository.findAll(pageable);
        }

        // Get user favorites
        Set<UUID> favoriteIds = currentUserId != null
                ? new HashSet<>(favoriteItemRepository.findMenuItemIdsByUserId(currentUserId))
                : Collections.emptySet();

        List<MenuItemResponse> items = itemPage.getContent().stream()
                .map(item -> toMenuItemResponse(item, favoriteIds.contains(item.getId())))
                .collect(Collectors.toList());

        return PagedResponse.<MenuItemResponse>builder()
                .content(items)
                .page(itemPage.getNumber())
                .size(itemPage.getSize())
                .totalElements(itemPage.getTotalElements())
                .totalPages(itemPage.getTotalPages())
                .last(itemPage.isLast())
                .first(itemPage.isFirst())
                .build();
    }

    public MenuItemResponse getMenuItemById(UUID itemId, UUID currentUserId) {
        MenuItem item = menuItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));

        boolean isFavorite = currentUserId != null
                && favoriteItemRepository.existsByUserIdAndMenuItemId(currentUserId, itemId);

        return toMenuItemResponse(item, isFavorite);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public MenuItemResponse createMenuItem(MenuItemRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        MenuItem item = MenuItem.builder()
                .category(category)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .discountedPrice(request.getDiscountedPrice())
                .imageUrl(request.getImageUrl())
                .isVegetarian(request.getIsVegetarian())
                .isVegan(request.getIsVegan())
                .isGlutenFree(request.getIsGlutenFree())
                .isAvailable(request.getIsAvailable())
                .preparationTimeMins(request.getPreparationTimeMins())
                .calories(request.getCalories())
                .sortOrder(request.getSortOrder())
                .stockQuantity(request.getStockQuantity())
                .build();

        return toMenuItemResponse(menuItemRepository.save(item), false);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public MenuItemResponse updateMenuItem(UUID itemId, MenuItemRequest request) {
        MenuItem item = menuItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        item.setCategory(category);
        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setPrice(request.getPrice());
        item.setDiscountedPrice(request.getDiscountedPrice());
        item.setImageUrl(request.getImageUrl());
        item.setIsVegetarian(request.getIsVegetarian());
        item.setIsVegan(request.getIsVegan());
        item.setIsGlutenFree(request.getIsGlutenFree());
        item.setIsAvailable(request.getIsAvailable());
        item.setPreparationTimeMins(request.getPreparationTimeMins());
        item.setCalories(request.getCalories());
        item.setSortOrder(request.getSortOrder());
        item.setStockQuantity(request.getStockQuantity());

        return toMenuItemResponse(menuItemRepository.save(item), false);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public void deleteMenuItem(UUID itemId) {
        if (!menuItemRepository.existsById(itemId)) {
            throw new ResourceNotFoundException("Menu item not found");
        }
        menuItemRepository.deleteById(itemId);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public void updateAvailability(UUID itemId, boolean available) {
        menuItemRepository.updateAvailability(itemId, available);
    }

    // ==================== MAPPERS ====================

    private CategoryResponse toCategoryResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .itemCount(category.getMenuItems() != null
                        ? (int) category.getMenuItems().stream().filter(MenuItem::getIsAvailable).count()
                        : 0)
                .sortOrder(category.getSortOrder())
                .isActive(category.getIsActive())
                .build();
    }

    private MenuItemResponse toMenuItemResponse(MenuItem item, boolean isFavorite) {
        return MenuItemResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .description(item.getDescription())
                .price(item.getPrice())
                .discountedPrice(item.getDiscountedPrice())
                .imageUrl(item.getImageUrl())
                .isVegetarian(item.getIsVegetarian())
                .isVegan(item.getIsVegan())
                .isGlutenFree(item.getIsGlutenFree())
                .isAvailable(item.getIsAvailable())
                .preparationTime(item.getPreparationTimeMins())
                .calories(item.getCalories())
                .categoryName(item.getCategory() != null ? item.getCategory().getName() : null)
                .categoryId(item.getCategory() != null ? item.getCategory().getId() : null)
                .isFavorite(isFavorite)
                .build();
    }
}
