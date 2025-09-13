package ru.sber.qa.llmdemo.giga.step;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.json.JSONObject;
import ru.sber.qa.llmdemo.giga.GigaService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class StepClassifier {
    protected static final Logger log = Logger.getInstance(StepClassifier.class);

    private static final Map<StepType, List<Pattern>> PATTERNS = Map.of(
            StepType.DB, List.of(
                    Pattern.compile("(?:БД|база|таблица|SQL|запрос|SELECT|INSERT|UPDATE|DELETE)"),
                    Pattern.compile("(?:поле|значение|запись|строк[а-я]+)"),
                    Pattern.compile("(?:status_id|created_at|updated_at)")
            ),
            StepType.API, List.of(
                    Pattern.compile("(?:API|эндпоинт|REST|HTTP|GET|POST|PUT|DELETE)"),
                    Pattern.compile("(?:ответ|код|статус|headers|body|json|xml)"),
                    Pattern.compile("/api/v\\d+/")
            ),
            StepType.UI, List.of(
                    Pattern.compile("(?:страниц|интерфейс|кнопк|поле|чекбокс|меню)"),
                    Pattern.compile("(?:нажать|ввести|выбрать|кликнуть|открыть)"),
                    Pattern.compile("(?:отображается|виден|скрыт|доступен)")
            ),
            StepType.KAFKA, List.of(
                    Pattern.compile("(?:Kafka|топик|сообщение|producer|consumer)"),
                    Pattern.compile("(?:отправить|получить|прочитать|событие)"),
                    Pattern.compile("(?:topic|partition|offset)")
            )
    );

    public enum StepType {
        DB, API, UI, KAFKA, UNKNOWN
    }

    public static StepType classify(String stepText, Project project) {
        StepType basicType = StepType.UNKNOWN;

        // Сначала проверяем по ключевым словам
        for (Map.Entry<StepType, List<Pattern>> entry : PATTERNS.entrySet()) {
            if (entry.getValue().stream()
                    .anyMatch(pattern -> pattern.matcher(stepText).find())) {
                basicType = entry.getKey();
            }
        }

        if (basicType != StepType.UNKNOWN) {
            return basicType;
        }
        return classifyWithLLM(stepText, project);
    }

    // Дополнительный метод с использованием LLM для сложных случаев
    private static StepType classifyWithLLM(String stepText, Project project) {
        String prompt = String.format(
                """
                        Определи тип шага автоматизации:
                        Шаг: "%s"
                        Варианты: DB, API, UI, KAFKA
                        Ответь только одним словом из вариантов""", stepText);

        ChatResponse response = project.getService(GigaService.class).getModel().chat(new UserMessage(prompt));

        if (response == null) {
            return StepType.UNKNOWN;
        }
        try {
            return StepType.valueOf(response.aiMessage().text().toUpperCase());
        } catch (IllegalArgumentException e) {
            return StepType.UNKNOWN;
        }

    }


    public static String getStepText(String stepText, Project project) {
        String prompt = String.format(
                """
                        Опиши 3-4мя словами что делает код без дополнительных слов и без попыток подробнее:
                        Шаг:
                        ```java
                        %s
                        ```
                        """, stepText);

        ChatResponse response = project.getService(GigaService.class).getModel().chat(new UserMessage(prompt));
        return response.aiMessage().text();
    }

    /**
     * Метод для анализа и описания шага автотеста, который не имеет аннотации.
     *
     * @param stepText     Текст функции
     * @param autoStepType Тип шага (Python или Java)
     * @param project
     * @return {@code Map<String, Object>} с ключами:
     * - "stepType": тип шага (DB, API, UI, KAFKA, UNKNOWN)
     * - "stepText": краткое описание шага
     */
    public static Map<String, Object> describeNotAnnotatedStep(String stepText, AutoStepType autoStepType, Project project) {
        String prompt = String.format(
                """ 
                        # Будет представлена функция, используемая в автотесте.
                        # Необходимо:
                            1. Определить тип шага автоматизациия из представленных: DB, API, UI, KAFKA.
                            2. Кратко опиши действие представленное в функции.
                        # Правила:
                            1. Тип шага описывается однозначно.
                            2. Описание шага не более 6 слов.
                        # Ответ в формате JSON без форматирования:
                            {
                                "type": "<тип шага>",
                                "text": "<описание функции>"
                            }
                        # Функция:
                            '''java
                                %s
                            '''
                        """, stepText);
        String response = project.getService(GigaService.class).getModel().chat(new SystemMessage(prompt)).aiMessage().text();

        Map<String, Object> result = new HashMap<>();
        try {
            JSONObject description = new JSONObject(response);
            String stepType = description.optString("type", "UNKNOWN");
            String stepDesc = description.optString("text", stepText);
            result.put("stepType", stepType.toUpperCase());
            result.put("stepText", stepDesc);
        } catch (Exception exc) {
            log.warn("Error in description not annotated step: " + exc);
            result.put("stepType", StepType.UNKNOWN);
            result.put("stepText", stepText);
        }

        if (result.get("stepType") instanceof String type) {
            try {
                result.put("stepType", StepType.valueOf(type));
            } catch (IllegalArgumentException e) {
                result.put("stepType", StepType.UNKNOWN);
            }
        }

        return result;
    }
}