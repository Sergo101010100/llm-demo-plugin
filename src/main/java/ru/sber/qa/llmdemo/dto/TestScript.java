package ru.sber.qa.llmdemo.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class TestScript {
    private int id;
    private String type;
    private List<Step> steps;

}