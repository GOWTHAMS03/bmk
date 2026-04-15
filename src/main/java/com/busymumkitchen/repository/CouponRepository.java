package com.busymumkitchen.repository;

import com.busymumkitchen.model.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    Page<Coupon> findByIsActiveTrue(Pageable pageable);

    List<Coupon> findByIsActiveTrueAndValidFromBeforeAndValidUntilAfter(
            LocalDateTime now1, LocalDateTime now2);
}
