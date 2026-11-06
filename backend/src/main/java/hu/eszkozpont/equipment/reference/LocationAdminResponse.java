package hu.eszkozpont.equipment.reference;

public record LocationAdminResponse(Long id, String code, String name, String building, String room, boolean active, long version) {}
