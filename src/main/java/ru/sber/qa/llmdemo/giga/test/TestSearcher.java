package ru.sber.qa.llmdemo.giga.test;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCodeBlock;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastFacade;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ru.sber.qa.llmdemo.dto.Step;
import ru.sber.qa.llmdemo.giga.GigaService;
import ru.sber.qa.llmdemo.giga.step.StepUtils;
import ru.sber.qa.llmdemo.giga.store.StoreService;
import ru.sber.qa.llmdemo.index.TestLanguage;
import ru.sber.qa.llmdemo.index.TmsTest;
import ru.sber.qa.llmdemo.utils.ResourcesUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class TestSearcher {
    protected static final com.intellij.openapi.diagnostic.Logger log = Logger.getInstance(TestSearcher.class);
    private static final String TESTS_STORE_SUFFIX = ".test.store";

    private static final String META_DATA_TEST_KEY = "testKey";
    private static final String META_DATA_TEST_BODY = "testBody";
    private static final String META_DATA_TEST_DESCRIPTION = "testDescription";
    private static final String META_DATA_TEST_TYPE = "testType";
    private static final String META_DATA_TEST_KEYWORDS = "testKeywords";
    private static final String META_DATA_TEST_LANGUAGE = "testLanguage";
    private static final String META_DATA_BACKGROUND = "testIsBackground";


    private final ChatMemory memory;
    @Getter
    private final StoreService storeService;
    private final Project project;

    public TestSearcher(Project project, VirtualFile rootFile) {
        this.project = project;
        this.storeService = new StoreService(project, rootFile, TESTS_STORE_SUFFIX);
        memory = MessageWindowChatMemory.withMaxMessages(4);
    }

    /**
     * Создает AutoTest из TmsTest.
     *
     * @param test TmsTest
     * @return AutoTest
     * @throws IllegalStateException если тип теста не определен или не поддерживается
     */
    private @NotNull AutoTest createAutoTestFromTest(TmsTest test) {
        AutoTest autoTest = new AutoTest();
        autoTest.setKey(test.getKey());


        UMethod uMethod = ReadAction.compute(
                () -> (UMethod) UastFacade.INSTANCE.convertElementWithParent(test.getTestProject(project), UMethod.class));
        if (uMethod != null) {
            return ApplicationManager.getApplication().runReadAction((Computable<AutoTest>) () -> {
                autoTest.setBody(uMethod.getJavaPsi().getText());
                if (uMethod.getSourcePsi() != null) {
                    autoTest.setLanguage(TestLanguage.valueOf(uMethod.getSourcePsi().getLanguage().getID().toUpperCase()));
                } else {
                    autoTest.setLanguage(TestLanguage.JAVA);
                }

                JSONObject meta = generateTestMetadata(uMethod);
                autoTest.setLlmDescription(meta.optString("description", ""));
                autoTest.setType(AutoTestType.getAutoTestType(meta.optString("type", "")));

                JSONArray keywordsJson = meta.optJSONArray("keywords");
                if (keywordsJson != null) {
                    autoTest.setKeywords(StreamSupport.stream(keywordsJson.spliterator(), false)
                            .map(Object::toString)
                            .map(String::toLowerCase)
                            .distinct()
                            .collect(Collectors.joining(", ")));
                }

                return autoTest;
            });
        }

        throw new IllegalStateException("Unexpected test type: " + test.getTestProject(project));
    }


    public List<CompletableFuture<Integer>> cacheExistingTests(List<TmsTest> tests, ProgressIndicator indicator) {
        // 2. Параллельная обработка с батчингом
        int batchSize = 10; // Размер батча для
        AtomicInteger processed = new AtomicInteger();

        List<AutoTest> batch = new ArrayList<>(batchSize);
        //todo тут мог бы быть parallelStream, но gigachat ругается 429
        tests.forEach(t -> {
            try {
                AutoTest autoStep = createAutoTestFromTest(t);
                if (indicator.isCanceled()) return;
                synchronized (batch) {
                    batch.add(autoStep);
                    if (batch.size() >= batchSize) {
                        processBatch(batch, indicator, processed, tests.size());
                        batch.clear();
                    }
                }

            } catch (Exception e) {
                log.error("Произошла ошибка при обработке теста: " + ReadAction.compute(t::getKey), e);
            }

        });

        // Обработка последнего батча
        if (!batch.isEmpty() && !indicator.isCanceled()) {
            processBatch(batch, indicator, processed, tests.size());
        }
        indicator.setText2(null);
        return List.of(CompletableFuture.completedFuture(0));
    }


    protected void processBatch(List<AutoTest> batch,
                                ProgressIndicator indicator,
                                AtomicInteger processed,
                                int totalTests) {
        // Пакетное создание эмбеддингов
        List<TextSegment> segments = batch.stream()
                .filter(at -> at.getLlmDescription() != null)
                .map(this::createTextSegments)
                .toList();

        List<Embedding> embeddings = segments.stream().map(storeService::embedWithRetry).toList();
        storeService.getStore().addAll(embeddings, segments);

        // 6. Обновление прогресса
        int newProcessed = processed.addAndGet(batch.size());
        indicator.setText2("Обработано тестов " + newProcessed + " из " + totalTests);
        log.info("Обработано тестов " + newProcessed + " из " + totalTests);
    }

    private TextSegment createTextSegments(AutoTest test) {
        Metadata metadata = new Metadata();
        if (test.getKey() != null) {
            metadata.put(META_DATA_TEST_KEY, test.getKey());
        }
        metadata.put(META_DATA_TEST_DESCRIPTION, test.getLlmDescription());
        metadata.put(META_DATA_TEST_BODY, test.getBody());
        metadata.put(META_DATA_TEST_LANGUAGE, test.getLanguage().toString());

        // Добавляем ключевые слова
        metadata.put(META_DATA_TEST_KEYWORDS, test.getKeywords());
        metadata.put(META_DATA_TEST_TYPE, test.getType().toString());

        metadata.put(META_DATA_BACKGROUND, Boolean.toString(test.isBackground()));

        String enrichedText = formattedTest(test.getLlmDescription(), test.getType().name(), test.getKeywords());
        return new TextSegment(enrichedText, metadata);
    }

    private static @NotNull String formattedTest(String description, String type, String keywords) {
        return String.format(
                """
                        Description: %s
                        Type: %s
                        Keywords: %s
                        """,
                description,
                type,
                keywords
        );
    }

    public Set<AutoTest> findSimilarTests(List<Step> manualSteps, int maxExamples, TestLanguage language) {
        String manualTest = StepUtils.formatManualSteps(manualSteps);
        // 1. Генерация метаданных для ручного теста
        JSONObject meta = generateManualTestMetadata(manualTest);
        String description = meta.optString("description", "");
        String type = meta.optString("type");
        String keywords = StreamSupport.stream(meta.optJSONArray("keywords").spliterator(), false)
                .map(Object::toString)
                .collect(Collectors.joining(", "));

        // 2. Формирование обогащенного текста для эмбеддинга
        String enrichedText = formattedTest(
                description,
                type,
                String.join(", ", keywords));

        var autoTests = storeService.searchSimilarItems(
                        enrichedText,
                        StoreService.SearchParams.builder()
                                .maxResults(maxExamples)
                                .filters(Collections.singletonList(new IsEqualTo(META_DATA_TEST_LANGUAGE, language.toString())))
                                .filters(Collections.singletonList(new IsEqualTo(META_DATA_BACKGROUND, "false")))
                                .useKeywordBonus(true)
                                .queryKeywords(List.of(keywords.split(", ")))
                                .build()
                ).stream()
                .map(TestSearcher::matchToAutoTest)
                .collect(Collectors.toSet());
        log.info("Найденные похожие тесты: " + autoTests);
        return autoTests;
    }

    public Collection<AutoTest> findSimilarTests(String description, int maxExamples, TestLanguage language) {

        return storeService.searchSimilarItems(
                        "Description: %s".formatted(description),
                        StoreService.SearchParams.builder()
                                .maxResults(maxExamples)
                                .filters(Collections.singletonList(new IsEqualTo(META_DATA_TEST_LANGUAGE, language.toString())))
                                .filters(Collections.singletonList(new IsEqualTo(META_DATA_BACKGROUND, "false")))
                                .build()
                ).stream()
                .map(TestSearcher::matchToAutoTest)
                .collect(Collectors.toSet());
    }

    private static AutoTest matchToAutoTest(EmbeddingMatch<TextSegment> match) {
        var metadata = match.embedded().metadata();
        var autoTest = new AutoTest();
        if (metadata != null) {
            autoTest.setKey(metadata.getString(META_DATA_TEST_KEY));
            autoTest.setBody(metadata.getString(META_DATA_TEST_BODY));
            autoTest.setLanguage(TestLanguage.valueOf(metadata.getString(META_DATA_TEST_LANGUAGE).toUpperCase()));
            autoTest.setLlmDescription(metadata.getString(META_DATA_TEST_DESCRIPTION));
            autoTest.setType(AutoTestType.valueOf(metadata.getString(META_DATA_TEST_TYPE)));
            autoTest.setKeywords(metadata.getString(META_DATA_TEST_KEYWORDS));
            autoTest.setBackground(Boolean.parseBoolean(metadata.getString(META_DATA_BACKGROUND)));

        }
        return autoTest;
    }

    private JSONObject generateMetadata(String sourceType, String codeType, String text) {
        memory.add(PromptTemplate.from(ResourcesUtils.getPrompt("prompts/test_analysis.md")).apply(Map.of("testType", sourceType)).toSystemMessage());
        var userMessage = UserMessage.from(
                """
                        ```%s
                        %s
                        ```
                        """.formatted(codeType, text));
        memory.add(userMessage);
        var aiMessage = project.getService(GigaService.class).getModel().chat(memory.messages()).aiMessage();
        String response = aiMessage.text();
        memory.clear();
        log.debug("Метадата для теста: " + response);
        try {
            return new JSONObject(response);
        } catch (JSONException e) {
            return new JSONObject()
                    .put("description", "Ошибка генерации описания")
                    .put("keywords", List.of())
                    .put("type", "");
        }
    }

    private JSONObject generateManualTestMetadata(String manualTestText) {
        return generateMetadata("ручной тест", "", manualTestText);
    }


    private JSONObject generateTestMetadata(UMethod testMethod) {
        String testBody = Optional.ofNullable(testMethod.getJavaPsi().getBody())
                .map(PsiCodeBlock::getStatements)
                .map(stmts -> Arrays.stream(stmts).limit(10)
                        .map(t -> ReadAction.compute(t::getText))
                        .collect(Collectors.joining("\n")))
                .orElse("// тело метода отсутствует");

        return generateMetadata("JUnit автотест", "java", testBody);
    }
}
