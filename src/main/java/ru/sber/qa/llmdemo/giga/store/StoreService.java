package ru.sber.qa.llmdemo.giga.store;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.Getter;
import ru.sber.qa.llmdemo.giga.GigaService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.UnaryOperator;

import static ru.sber.qa.llmdemo.giga.step.StepUtils.getExamples;

public class StoreService {
    protected static final Logger log = Logger.getInstance(StoreService.class);
    private static final double DEFAULT_THRESHOLD = 0.8;
    private static final int DEFAULT_MULTIPLIER = 3;

    public static final String META_DATA_EXAMPLE = "stepExample";
    public static final String META_DATA_FRAMEWORK = "framework";
    public static final String META_DATA_STEP_TYPE = "stepType";

    protected final Project project;
    protected final String rootPath;
    @Getter
    protected final InMemoryEmbeddingStore<TextSegment> store;
    private final String prefix;

    public StoreService(Project project, VirtualFile rootFile, String prefix) {
        this.project = project;
        this.rootPath = rootFile.getPath();
        this.prefix = prefix;

        Path storePath = Path.of(rootPath, project.getName() + prefix);
        if (Files.exists(storePath)) {
            this.store = InMemoryEmbeddingStore.fromFile(storePath);
        } else {
            this.store = new InMemoryEmbeddingStore<>();
        }
    }

    protected List<Embedding> embedAllWithRetry(List<TextSegment> segments) {
        return project.getService(GigaService.class).getEmbeddingModel().embedAll(segments).content();
    }

    public Embedding embedWithRetry(TextSegment segment) {
        return project.getService(GigaService.class).getEmbeddingModel().embed(segment).content();

    }

    private Embedding embedText(String text) {
        return project.getService(GigaService.class).getEmbeddingModel().embed(text).content();
    }

    public EmbeddingSearchResult<TextSegment> search(String query, UnaryOperator<EmbeddingSearchRequest.EmbeddingSearchRequestBuilder> builderOperator) {
        Embedding queryEmbedding = embedText(query);
        return store.search(builderOperator.apply(EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding)).build());
    }

    public void clear() {
        store.removeAll();
        log.info("Clearing store");
    }

    public void saveStoreToFile() {
        Path storePath = Path.of(rootPath, project.getName() + prefix);
        store.serializeToFile(storePath);
        log.info("Store saved to " + storePath);
    }

    /**
     * Универсальный метод поиска похожих элементов
     *
     * @param text   Поисковый запрос
     * @param params Параметры поиска
     * @return Список совпадений, отсортированный по релевантности
     */
    public List<EmbeddingMatch<TextSegment>> searchSimilarItems(String text, SearchParams params) {
        // 1. Валидация входных данных
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Подготовка запроса
        EmbeddingSearchRequest request = prepareSearchRequest(text, params);

        // 3. Выполнение поиска
        List<EmbeddingMatch<TextSegment>> matches = executeSearch(request);

        // 4. Реранжирование результатов
        return rerankMatches(matches, params);
    }

    private EmbeddingSearchRequest prepareSearchRequest(String text, SearchParams params) {
        // Базовые параметры запроса
        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder builder = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedText(text))
                .maxResults(params.maxResults() * DEFAULT_MULTIPLIER)
                .minScore(Optional.ofNullable(params.threshold()).orElse(DEFAULT_THRESHOLD));

        // Добавляем фильтры из параметров
        params.filters().forEach(builder::filter);

        return builder.build();
    }

    private List<EmbeddingMatch<TextSegment>> executeSearch(EmbeddingSearchRequest request) {
        return store.search(request)
                .matches()
                .stream()
                .filter(match -> match.embedded() != null)
                .toList();
    }

    private List<EmbeddingMatch<TextSegment>> rerankMatches(List<EmbeddingMatch<TextSegment>> matches,
                                                            SearchParams params) {
        return matches.stream()
                .sorted(Comparator.comparingDouble(match ->
                        -calculateCombinedScore(match, params)))
                .limit(params.maxResults())
                .toList();
    }

    private double calculateCombinedScore(EmbeddingMatch<TextSegment> match, SearchParams params) {
        double baseScore = match.score();
        double examplesBonus = params.useExamplesBonus()
                ? getExamples(match.embedded()).size() * 0.01
                : 0;
        double keywordBonus = params.useKeywordBonus()
                ? calculateKeywordBonus(match.embedded().text(), params.queryKeywords())
                : 0;

        return baseScore * (1 + examplesBonus + keywordBonus);
    }

    private double calculateKeywordBonus(String text, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return 0;

        long matches = keywords.stream()
                .filter(keyword -> text.toLowerCase().contains(keyword.toLowerCase()))
                .count();

        return matches * 0.05; // 5% за каждое совпадение ключевого слова
    }

    // Параметры поиска
    @lombok.Builder
    public record SearchParams(
            int maxResults,
            Double threshold,
            List<Filter> filters,
            boolean useExamplesBonus,
            boolean useKeywordBonus,
            List<String> queryKeywords
    ) {
    }
}