package ru.sber.qa.llmdemo.giga.store;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import dev.langchain4j.data.document.DefaultDocument;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import ru.sber.qa.llmdemo.giga.step.AutoStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static ru.sber.qa.llmdemo.giga.GigaService.SPLITTER;


public class StepStoreService extends StoreService {

    private static final String STEPS_STORE_SUFFIX = ".steps.store";

    public StepStoreService(Project project, VirtualFile rootFile) {
        super(project, rootFile, STEPS_STORE_SUFFIX);
    }

    public void updateSteps(Set<AutoStep> steps) {
        steps.stream()
                .map(this::createTextSegments)
                .forEach(segments -> {
                    var embeddings = embedAllWithRetry(segments);
                    store.addAll(embeddings, segments);
                });
    }

    private List<TextSegment> createTextSegments(AutoStep step) {
        if (step.getStepText() == null || step.getStepText().isBlank()) {
            return new ArrayList<>();
        }
        Metadata metadata = new Metadata();
        metadata.put(META_DATA_EXAMPLE, step.getJsonExamples());
        metadata.put(META_DATA_FRAMEWORK, step.getAutoStepType().toString());
        metadata.put(META_DATA_STEP_TYPE, step.getType().toString());
        Document document = new DefaultDocument(step.getStepText(), metadata);
        return SPLITTER.split(document);
    }
}