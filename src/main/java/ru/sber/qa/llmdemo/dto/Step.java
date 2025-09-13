package ru.sber.qa.llmdemo.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Step {
    private String expectedResult;
    private String description;
    private int index;
    private int id;
    private String testData;


    @JsonIgnore
    public String toAgentString() {
        return "index:%s\ndescription: %s\nexpectedResult: %s\ntestData: %s\n"
                .formatted(getIndex(), getDescription(), getExpectedResult(), getTestData());
    }
}