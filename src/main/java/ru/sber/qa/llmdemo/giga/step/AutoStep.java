package ru.sber.qa.llmdemo.giga.step;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@Data
public class AutoStep {
    private String stepText;
    private StepClassifier.StepType type;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @EqualsAndHashCode.Exclude
    private Set<String> examples;

    @EqualsAndHashCode.Exclude
    private AutoStepType autoStepType;

    // для необъявленных шагов
    private boolean notAnnotatedStep;


    public String getJsonExamples() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writer().writeValueAsString(this.getExamples());
        } catch (JsonProcessingException e) {
            return "";
        }
    }

}
