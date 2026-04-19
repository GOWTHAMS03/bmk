package com.busymumkitchen.dto.menu;

import jakarta.validation.constraints.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MenuItemRequest {


    @NotNull(message = "Category ID is required")
    private UUID categoryId;

    @NotBlank(message = "Item name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    @DecimalMin(value = "0.00", message = "Discounted price cannot be negative")
    private BigDecimal discountedPrice;

    private String imageUrl;
    private Boolean vegetarian = false;
    private Boolean vegan = false;
    private Boolean glutenFree = false;
    private Boolean available = true;

    @Min(value = 1, message = "Preparation time must be at least 1 minute")
    private Integer preparationTime;

    private Integer calories;
    private Integer sortOrder = 0;
    private Integer stockQuantity = -1;
}
