package com.busymumkitchen.repository;

import com.busymumkitchen.model.FavoriteItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FavoriteItemRepository extends JpaRepository<FavoriteItem, UUID> {

    Page<FavoriteItem> findByUserId(UUID userId, Pageable pageable);

    Optional<FavoriteItem> findByUserIdAndMenuItemId(UUID userId, UUID menuItemId);

    boolean existsByUserIdAndMenuItemId(UUID userId, UUID menuItemId);

    void deleteByUserIdAndMenuItemId(UUID userId, UUID menuItemId);

    @Query("SELECT f.menuItemId FROM FavoriteItem f WHERE f.user.id = :userId")
    List<UUID> findMenuItemIdsByUserId(@Param("userId") UUID userId);
}
