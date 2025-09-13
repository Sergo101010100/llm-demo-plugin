package ru.sber.qa.llmdemo.giga.step;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.sber.qa.llmdemo.dto.Step;
import ru.sber.qa.llmdemo.giga.store.StepStoreService;
import ru.sber.qa.llmdemo.giga.store.StoreService;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static ru.sber.qa.llmdemo.giga.store.StoreService.META_DATA_FRAMEWORK;
import static ru.sber.qa.llmdemo.giga.store.StoreService.META_DATA_STEP_TYPE;

public class StepSearcher {
    protected static final com.intellij.openapi.diagnostic.Logger log = Logger.getInstance(StepSearcher.class);

    protected final Project project;

    private static final int MAX_SIMILAR_STEPS = 5;
    private static final double SIMILARITY_THRESHOLD = 0.8;
    private static final double MIN_SIMILARITY_THRESHOLD = 0.6;

    @Getter
    private final StepStoreService storeService;

    public StepSearcher(Project project, VirtualFile rootPath) {
        this.project = project;
        this.storeService = new StepStoreService(project, rootPath);
    }

    public Collection<TextSegment> findSimilar(Step step, AutoStepType aupType) {

        // Сначала ищем по всему тексту
        List<EmbeddingMatch<TextSegment>> allMatches = new ArrayList<>(searchComponent(
                step.getDescription() + step.getTestData() + step.getExpectedResult(),
                SIMILARITY_THRESHOLD, null, aupType));

        // Обрабатываем компоненты отдельно
        List<EmbeddingMatch<TextSegment>> descriptionMatches = searchComponent(
                step.getDescription(), SIMILARITY_THRESHOLD, null, aupType);
        allMatches.addAll(descriptionMatches);

        if (step.getTestData() != null) {
            List<EmbeddingMatch<TextSegment>> testDataMatches = searchComponent(step.getTestData(),
                    SIMILARITY_THRESHOLD, null, aupType);
            allMatches.addAll(testDataMatches);
        }

        if (step.getExpectedResult() != null) {
            List<EmbeddingMatch<TextSegment>> expectedMatches = searchComponent(step.getExpectedResult(),
                    SIMILARITY_THRESHOLD, null, aupType);
            allMatches.addAll(expectedMatches);
        }

        // Понижаем порог, но добавляем фильтрацию по типу шага
        StepClassifier.StepType descriptionType = StepClassifier.classify(step.getDescription(), project);
        allMatches.addAll(searchComponent(step.getDescription(),
                MIN_SIMILARITY_THRESHOLD, descriptionType, aupType));
        if (step.getTestData() != null) {
            StepClassifier.StepType stepTypeTestData = StepClassifier.classify(step.getTestData(), project);
            allMatches.addAll(searchComponent(step.getTestData(),
                    MIN_SIMILARITY_THRESHOLD, stepTypeTestData, aupType));
        }
        if (step.getExpectedResult() != null) {
            StepClassifier.StepType expectedResultType = StepClassifier.classify(step.getExpectedResult(), project);
            allMatches.addAll(searchComponent(step.getExpectedResult(),
                    MIN_SIMILARITY_THRESHOLD, expectedResultType, aupType));
        }


        return allMatches.stream()
                .map(EmbeddingMatch::embedded)
                .collect(Collectors.toUnmodifiableSet());
    }

    public List<CompletableFuture<Integer>> updateStepsInt(Set<AutoStep> steps, @NotNull ProgressIndicator indicator) {
        storeService.updateSteps(steps);
        return List.of(CompletableFuture.completedFuture(0));
    }

    public List<TextSegment> findSimilar(String step, AutoStepType aupType) {
        StepClassifier.StepType stepType
                = StepClassifier.classify(step, project);
        return searchComponent(step, SIMILARITY_THRESHOLD, stepType, aupType)
                .stream()
                .map(EmbeddingMatch::embedded)
                .toList();
    }

    private List<EmbeddingMatch<TextSegment>> searchComponent(String text, Double threshold,
                                                              @Nullable StepClassifier.StepType type,
                                                              @NotNull AutoStepType aupType) {

        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        List<Filter> filters = new ArrayList<>();
        filters.add(new IsEqualTo(META_DATA_FRAMEWORK, aupType.toString()));
        if (type != null) {
            filters.add(new IsEqualTo(META_DATA_STEP_TYPE, type.toString()));
        }
        StoreService.SearchParams.SearchParamsBuilder builder = StoreService.SearchParams.builder();
        builder.maxResults(MAX_SIMILAR_STEPS)
                .filters(filters)
                .threshold(threshold)
                .useExamplesBonus(true);

        return storeService.searchSimilarItems(
                text,
                builder.build()
        );
    }

    /**
     * Приводит неаннотированные шаги в стандартному виду AutoStep и запускает загрузку всех шагов.
     */
    public List<CompletableFuture<Integer>> updateSteps(Set<AutoStep> steps, @NotNull ProgressIndicator indicator) {
        steps.forEach(step -> {
            if (step.isNotAnnotatedStep()) {
                Map<String, Object> description = StepClassifier.describeNotAnnotatedStep(step.getStepText(), step.getAutoStepType(), project);
                step.setStepText((String) description.get("stepText"));
                step.setType((StepClassifier.StepType) description.get("stepType"));
            }
        });
        return updateStepsInt(steps, indicator);
    }
}
