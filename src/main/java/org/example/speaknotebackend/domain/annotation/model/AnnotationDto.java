package org.example.speaknotebackend.domain.annotation.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnnotationDto {
    private String text;
    private float x;
    private float y;
    private int pageNumber;
}
