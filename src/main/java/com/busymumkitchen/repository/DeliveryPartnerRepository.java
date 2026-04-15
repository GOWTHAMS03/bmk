package com.busymumkitchen.repository;

import com.busymumkitchen.model.DeliveryPartner;
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
public interface DeliveryPartnerRepository extends JpaRepository<DeliveryPartner, UUID> {

    Optional<DeliveryPartner> findByUserId(UUID userId);

    List<DeliveryPartner> findByIsAvailableTrueAndIsVerifiedTrue();

    @Query("SELECT dp FROM DeliveryPartner dp WHERE dp.isAvailable = true " +
            "AND dp.isVerified = true " +
            "AND dp.currentLatitude IS NOT NULL AND dp.currentLongitude IS NOT NULL")
    List<DeliveryPartner> findAvailableWithLocation();

    Page<DeliveryPartner> findByIsVerified(Boolean isVerified, Pageable pageable);
}
