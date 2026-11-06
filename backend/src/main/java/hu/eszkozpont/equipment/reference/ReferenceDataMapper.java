package hu.eszkozpont.equipment.reference;

import hu.eszkozpont.equipment.*;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ReferenceDataMapper {
    CategoryAdminResponse toResponse(EquipmentCategory category);
    LocationAdminResponse toResponse(StorageLocation location);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    EquipmentCategory fromCreate(CategoryCreateRequest request);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    void update(CategoryUpdateRequest request, @MappingTarget EquipmentCategory category);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "code", source = "code")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "building", source = "building")
    @Mapping(target = "room", source = "room")
    StorageLocation fromCreate(LocationCreateRequest request);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "code", source = "code")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "building", source = "building")
    @Mapping(target = "room", source = "room")
    void update(LocationUpdateRequest request, @MappingTarget StorageLocation location);
}
