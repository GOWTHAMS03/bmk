package com.busymumkitchen.repository;

import com.busymumkitchen.model.MenuItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    
    Page<MenuItem> findByCategoryIdAndIsAvailableTrue(UUID categoryId, Pageable pageable);

    @Query("SELECT m FROM MenuItem m WHERE m.isAvailable = true " +
            "AND (LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(m.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<MenuItem> searchAll(@Param("search") String search, Pageable pageable);
    @Query("SELECT m FROM MenuItem m WHERE m.isAvailable = true ORDER BY m.sortOrder ASC")
    List<MenuItem> findAvailable();

    List<MenuItem> findByIdIn(List<UUID> ids);

    @Modifying
    @Query("UPDATE MenuItem m SET m.isAvailable = :available WHERE m.id = :id")
    void updateAvailability(@Param("id") UUID id, @Param("available") boolean available);

    @Modifying
    @Query("UPDATE MenuItem m SET m.price = :price, m.discountedPrice = :discountedPrice WHERE m.id = :id")
    void updatePrice(@Param("id") UUID id,
                     @Param("price") BigDecimal price,
                     @Param("discountedPrice") BigDecimal discountedPrice);

    
}
