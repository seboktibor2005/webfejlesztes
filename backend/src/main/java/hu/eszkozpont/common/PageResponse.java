package hu.eszkozpont.common;

import org.springframework.data.domain.Page;
import java.util.List;

public record PageResponse<T>(List<T> content, PageMetadata page) {
    public record PageMetadata(int number, int size, long totalElements, int totalPages) {}
    public static <T> PageResponse<T> of(Page<T> source) {
        return new PageResponse<>(source.getContent(), new PageMetadata(source.getNumber(),
                source.getSize(), source.getTotalElements(), source.getTotalPages()));
    }
}
