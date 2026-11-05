package hu.eszkozpont.equipment;

import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EquipmentMapper {
    EquipmentResponse toResponse(EquipmentItem item);
    CategoryResponse toResponse(EquipmentCategory category);
    LocationResponse toResponse(StorageLocation location);
}
