package ru.sber.qa.llmdemo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TestScript {
    private int id;
    private String type;
    private List<Step> steps;

}