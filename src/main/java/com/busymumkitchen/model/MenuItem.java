package com.busymumkitchen.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "menu_items", indexes = {

        @Index(name = "idx_menu_category", columnList = "category_id"),
        @Index(name = "idx_menu_available", columnList = "is_available"),
        @Index(name = "idx_menu_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "discounted_price", precision = 10, scale = 2)
    private BigDecimal discountedPrice;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_vegetarian", nullable = false)
    @Builder.Default
    private Boolean isVegetarian = false;

    @Column(name = "is_vegan")
    @Builder.Default
    private Boolean isVegan = false;

    @Column(name = "is_gluten_free")
    @Builder.Default
    private Boolean isGlutenFree = false;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    @Column(name = "preparation_time_mins")
    private Integer preparationTimeMins;

    @Column(name = "calories")
    private Integer calories;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "stock_quantity")
    @Builder.Default
    private Integer stockQuantity = -1; // -1 means unlimited

    public BigDecimal getEffectivePrice() {
        return discountedPrice != null ? discountedPrice : price;
    }
}
