package org.example.speaknotebackend.common.response;


import java.util.List;

public record PagedResponse<T>(
        int page, int size, long totalElements, int totalPages, List<T> items
) {}
