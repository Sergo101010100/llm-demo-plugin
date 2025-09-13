package ru.sber.qa.llmdemo.giga;

import chat.giga.client.auth.AuthClient;
import chat.giga.client.auth.AuthClientBuilder;
import chat.giga.langchain4j.GigaChatChatModel;
import chat.giga.langchain4j.GigaChatChatRequestParameters;
import chat.giga.langchain4j.GigaChatEmbeddingModel;
import chat.giga.model.ModelName;
import chat.giga.model.Scope;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.ui.UIUtil;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONObject;
import ru.sber.qa.llmdemo.dto.Step;
import ru.sber.qa.llmdemo.giga.step.AutoStepType;
import ru.sber.qa.llmdemo.giga.step.JavaStepLoader;
import ru.sber.qa.llmdemo.giga.step.StepSearcher;
import ru.sber.qa.llmdemo.giga.step.StepUtils;
import ru.sber.qa.llmdemo.giga.test.AutoTest;
import ru.sber.qa.llmdemo.giga.test.TestSearcher;
import ru.sber.qa.llmdemo.index.TestLanguage;
import ru.sber.qa.llmdemo.index.TmsTest;
import ru.sber.qa.llmdemo.settings.AppSettingsState;
import ru.sber.qa.llmdemo.utils.FilteringClassLoader;
import ru.sber.qa.llmdemo.utils.ResourcesUtils;
import ru.sber.qa.llmdemo.utils.SlowUtils;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Getter
@Service(Service.Level.PROJECT)
public final class GigaService {
    private static final Logger log = Logger.getInstance(GigaService.class);
    private final Project project;

    private final StepSearcher stepSearcher;
    private final TestSearcher testSearcher;
    private final GigaAgent agent;
    private final GigaChatChatModel model;
    private final GigaChatEmbeddingModel embeddingModel;
    private final JavaStepLoader javaStepLoader;
    public static final DocumentSplitter SPLITTER = new DocumentBySentenceSplitter(512, 50);
    InMemoryChatMemoryStore storeDialog = new InMemoryChatMemoryStore();

    @Getter
    private final VirtualFile rootPath;

    public GigaService(Project project) {
        this.project = project;
        this.rootPath = provideRootPath();
        this.stepSearcher = new StepSearcher(project, rootPath);
        this.testSearcher = new TestSearcher(project, rootPath);
        this.javaStepLoader = new JavaStepLoader(project);

        String gigachatApiKey = ResourcesUtils.getPluginProperties().getProperty("gigachat.key");

        model = GigaChatChatModel.builder()
                .defaultChatRequestParameters(GigaChatChatRequestParameters.builder()
                        .modelName(ModelName.GIGA_CHAT_PRO)
                        .build())
                .authClient(AuthClient.builder()
                        .withOAuth(AuthClientBuilder.OAuthBuilder.builder()
                                .scope(Scope.GIGACHAT_API_PERS)
                                .authKey(gigachatApiKey)
                                .build())
                        .build())
                .build();

        embeddingModel = GigaChatEmbeddingModel.builder()
                .authClient(AuthClient.builder()
                        .withOAuth(AuthClientBuilder.OAuthBuilder.builder()
                                .scope(Scope.GIGACHAT_API_PERS)
                                .authKey(gigachatApiKey)
                                .build())
                        .build())
                .build();

        //делаем память для каждого теста отдельно
        ChatMemoryProvider chatMemoryProvider = testKey -> MessageWindowChatMemory.builder()
                .id(testKey)
                .maxMessages(200)
                .chatMemoryStore(storeDialog)
                .build();

        agent = AiServices.builder(GigaAgent.class)
                .chatModel(model)
                .chatMemoryProvider(chatMemoryProvider)
                .build();

    }

    private VirtualFile provideRootPath() {
        IProjectStore store = ProjectKt.getStateStore(project);
        Path baseDir = store.getProjectBasePath();
        VirtualFile ideaDir = VfsUtil.findFile(baseDir.resolve(Project.DIRECTORY_STORE_FOLDER), false);

        if (ideaDir != null) {
            AtomicReference<VirtualFile> result = new AtomicReference<>();
            UIUtil.invokeAndWaitIfNeeded(() -> {
                PsiDirectory psiIdeaDir = SlowUtils.runSyncWithProgress(project, "",
                        () -> PsiManager.getInstance(project).findDirectory(ideaDir));
                if (psiIdeaDir != null) {
                    PsiDirectory storeDir = SlowUtils.runSyncWithProgress(project, "",
                            () -> psiIdeaDir.findSubdirectory(".llmstore"));
                    if (storeDir == null) {
                        result.set(WriteAction.compute(() -> psiIdeaDir.createSubdirectory(".llmstore")).getVirtualFile());
                    } else {
                        result.set(storeDir.getVirtualFile());
                    }
                }
            });
            return result.get();
        }
        return null;
    }


    public String generateStep(TmsTest tmsTest, String targetClass, List<String> annotations, ProgressIndicator indicator) {
        //очищаем память и закрываем таб с чатом
        clearMemory(tmsTest);

        var totalFraction = 5d;
        List<Step> steps = tmsTest.getTest().getTestScript().getSteps();
        String preconditionText = tmsTest.getTest().getPrecondition();
        String objectiveText = tmsTest.getTest().getObjective();

        indicateProgress(indicator, "Разбиваем шаги", 1 / totalFraction);
        List<Step> criticSteps = reviewLlmTests(preconditionText, steps);

        indicateProgress(indicator, "Поиск похожих шагов", 2 / totalFraction);
        var similarSteps = findSimilarSteps(criticSteps);
        String similarStepsText = StepUtils.formatStepDefinitions(similarSteps);
        log.info("Найденные похожие шаги: " + similarStepsText);

        Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
        String javaVersion = sdk == null ?
                " 17 " :
                sdk.getVersionString();

        //добавляем примеры тестов
        indicateProgress(indicator, "Поиск похожих тестов", 3 / totalFraction);
        var testExamples = findSimilarTest(criticSteps);
        log.info("Найденные похожие тесты: " + testExamples);
        //добавляем класс
        String pattern = "";
        if (targetClass != null) {
            ChatResponse response = model.chat(PromptTemplate.from("""
                            Содержимое класса, в который добавляется новый тест.
                            Если в классе уже есть тесты, то выдели из них список ключевых классов и методов
                            Класс:
                            ```java
                            {{targetClass}}
                            ```
                            В ответе нужно указать используемые вспомогательные классы и методы без дополнительного форматирования
                            """)
                    .apply(Map.of("targetClass", targetClass))
                    .toUserMessage());
            pattern = response.aiMessage().text();
        }

        var additional = AppSettingsState.getInstance(project).getAdditionalInstructions();

        //И сам ручной тест
        String precondition = preconditionText.isEmpty()
                ? "Предусловия не указаны"
                : preconditionText;
        String objective = objectiveText.isEmpty()
                ? "Цель не указана"
                : objectiveText;

        indicateProgress(indicator, "Генерация Java-теста", 4 / totalFraction);

        String firstGenPrompt = ResourcesUtils.getPrompt("prompts/first_generation.md");

        String firstGenMessage = PromptTemplate.from(firstGenPrompt).apply(
                Map.of("manualSteps", StepUtils.formatManualSteps(criticSteps),
                        "precondition", precondition,
                        "objective", objective,
                        "pattern", pattern,
                        "testKey", tmsTest.getKey(),
                        "testName", tmsTest.getTest().getName(),
                        "defaultAnnotation", String.join(", ", annotations)
                )
        ).toUserMessage().singleText();

        //делаем первичную генерацию
        String generatedTest = FilteringClassLoader.runWithClassLoader(() -> agent.generateTest(tmsTest.getKey(),
                similarStepsText,
                javaVersion,
                testExamples,
                additional,
                firstGenMessage));
        log.info("Сгенерированный Junit-тест:\n " + generatedTest);

        indicateProgress(indicator, "Ревью сгенерированного Junit-теста", 5 / totalFraction);

        String reviewedTest = FilteringClassLoader.runWithClassLoader(() -> {
            //делаем доп ревью созданного теста
            String reviewPrompt = ResourcesUtils.getPrompt("prompts/first_generation.md");
            return agent.refineGeneratedTest(
                    tmsTest.getKey(),
                    PromptTemplate.from(reviewPrompt).apply(Map.of("additional", additional)).text(),
                    generatedTest);
        });
        log.info("Junit-тест после ревью:\n " + reviewedTest);
        return reviewedTest;
    }


    public void clearMemory(TmsTest tmsTest) {
        storeDialog.deleteMessages(tmsTest.getKey());
        TmsTestLLMToolWindow.closeTab(project, tmsTest);
    }

    private void indicateProgress(ProgressIndicator indicator, String text, double fraction) {
        if (indicator != null) {
            indicator.setText(text);
            indicator.setFraction(fraction);
        }
    }


    private List<Step> reviewLlmTests(String precondition, List<Step> manualSteps) {
        //если нет предусловия и нет шагов, то ничего не делаем
        if (precondition.isEmpty() &&
                (manualSteps.isEmpty() || manualSteps.stream().allMatch(step ->
                        step.getExpectedResult().isEmpty()
                                && step.getDescription().isEmpty()
                                && step.getTestData().isEmpty())
                )
        ) {
            return manualSteps;
        }

        List<Step> updatedSteps = new ArrayList<>();
        StringBuilder steps = new StringBuilder();

        //добавляем предусловие в начало в виде шага
        if (!precondition.isEmpty()) {
            Step preconditionStep = new Step();
            preconditionStep.setIndex(0);
            preconditionStep.setDescription(precondition);

            for (Step step : manualSteps) {
                step.setIndex(step.getIndex() + 1);
            }
            manualSteps.addFirst(preconditionStep);
        }

        //приводим шаги к формату для критика LLM
        for (Step step : manualSteps) {
            steps.append(step.toAgentString());
        }

        UserMessage criticPrompt = PromptTemplate.from(ResourcesUtils.getPrompt("prompts/handle_test.md")).apply(Map.of("handtest", steps)).toUserMessage();
        String newTest = model.chat(criticPrompt).aiMessage().text();
        JSONArray refactoredTest;
        try {
            refactoredTest = new JSONArray(newTest);
        } catch (Exception exc) {
            log.warn("Ошибка формата в ответе LLM" + newTest);
            return manualSteps;
        }

        for (int i = 0; i < refactoredTest.length(); i++) {
            JSONObject step = refactoredTest.getJSONObject(i);
            Step jsonStep = new Step();

            jsonStep.setDescription(step.optString("description", ""));
            jsonStep.setExpectedResult(step.optString("expectedResult", ""));
            jsonStep.setTestData(step.optString("testData", ""));
            jsonStep.setIndex(step.optInt("index", i));

            updatedSteps.add(jsonStep);
        }
        log.info("Обновленные шаги после обработки LLM: " + updatedSteps);
        return updatedSteps;
    }


    protected Collection<TextSegment> findSimilarSteps(List<Step> steps) {
        return steps.stream()
                .flatMap(step -> stepSearcher.findSimilar(step, AutoStepType.Junit).stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    public Collection<TextSegment> findSimilarStep(String step, AutoStepType type) {
        return stepSearcher.findSimilar(step, type);
    }

    private String findSimilarTest(List<Step> manualSteps) {
        Set<AutoTest> similarTests = testSearcher.findSimilarTests(manualSteps, 3, TestLanguage.JAVA);

        return similarTests.stream().map(AutoTest::toString).collect(Collectors.joining("\n============\n"));
    }
}
