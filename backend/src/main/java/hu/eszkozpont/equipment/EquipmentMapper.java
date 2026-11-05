package hu.eszkozpont.equipment;

import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EquipmentMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "inventoryCode", source = "inventoryCode")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    EquipmentItem fromCreate(EquipmentCreateRequest request);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    void update(EquipmentUpdateRequest request, @MappingTarget EquipmentItem item);

    EquipmentResponse toResponse(EquipmentItem item);
    CategoryResponse toResponse(EquipmentCategory category);
    LocationResponse toResponse(StorageLocation location);
}
