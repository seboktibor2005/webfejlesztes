package hu.eszkozpont.equipment;

public record EquipmentResponse(Long id, String inventoryCode, String name, String description,
        CategoryResponse category, LocationResponse location, EquipmentLifecycle lifecycle, long version) {}
