package ru.sber.qa.llmdemo.giga.step;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import lombok.experimental.UtilityClass;
import one.util.streamex.EntryStream;
import ru.sber.qa.llmdemo.dto.Step;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static ru.sber.qa.llmdemo.giga.store.StoreService.META_DATA_EXAMPLE;


@UtilityClass
public class StepUtils {

    private static final int MAX_EXAMPLES = 3;

    public static List<String> getExamples(TextSegment step) {
        try {
            return new ObjectMapper().readValue(step.metadata().getString(META_DATA_EXAMPLE), new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            //log.warn("Не удалось получить примеры шага '%s'".formatted(step.text()), e);
            return Collections.emptyList();
        }
    }

    public static String formatManualSteps(List<?> manualSteps) {
        BiFunction<Integer, Object, String> formatter = (Integer index, Object step) -> formatManualStep(index, (Step) step);
        return EntryStream.of(manualSteps)
                .mapKeyValue(formatter)
                .joining("\n");
    }

    private static String formatManualStep(Integer index, Step step) {
        return String.format("- Шаг %s: \"%s\"%s%s",
                index + 1,
                step.getDescription(),
                step.getTestData() != null ? "\n  Тестовые данные: " + step.getTestData() : "",
                step.getExpectedResult() != null ? "\n  Ожидаемый результат: " + step.getExpectedResult() : "");
    }


    public static String formatStepDefinitions(Collection<TextSegment> similarSteps) {
        return similarSteps.stream()
                .map(step -> {
                    List<String> examples = getExamples(step);
                    StringBuilder builder = new StringBuilder("- " + step.text());
                    if (!examples.isEmpty()) {
                        builder.append("\n=> Примеры использования:");
                    }
                    examples.stream().limit(MAX_EXAMPLES).forEach(ex ->
                            builder.append("\n").append("    - ").append(ex));
                    return builder.toString();
                })
                .collect(Collectors.joining("\n"));
    }
}
