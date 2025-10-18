package org.example.speaknotebackend.domain.annotation.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnnotationMessage {
    private String text;
    private int pageNumber;
    private float x;
    private float y;
}
