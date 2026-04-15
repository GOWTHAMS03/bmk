package com.busymumkitchen.repository;

import com.busymumkitchen.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByIsActiveTrueOrderBySortOrderAsc();

    List<Category> findAllByOrderBySortOrderAsc();

    boolean existsByNameIgnoreCase(String name);
}
