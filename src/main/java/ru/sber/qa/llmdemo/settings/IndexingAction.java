package ru.sber.qa.llmdemo.settings;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import ru.sber.qa.llmdemo.giga.GigaService;
import ru.sber.qa.llmdemo.giga.step.AutoStep;
import ru.sber.qa.llmdemo.index.TmsIndexUtil;
import ru.sber.qa.llmdemo.index.TmsTest;
import ru.sber.qa.llmdemo.utils.MessagesUtils;
import ru.sber.qa.llmdemo.utils.TmsMessage;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;


public class IndexingAction implements ActionListener {
    private static final Logger log = Logger.getInstance(IndexingAction.class);

    private final Project project;


    public IndexingAction(Project project) {
        this.project = project;
    }


    @Override
    public void actionPerformed(ActionEvent e) {


        //запускаем фоновый процесс загрузки шагов
        new Task.Backgroundable(project, MessagesUtils.get("indexing.project"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                if (DumbService.isDumb(project)) {
                    DumbService.getInstance(project).runWhenSmart(() -> indexProject(indicator));
                    return;
                }
                indexProject(indicator);
            }

            private void indexProject(ProgressIndicator indicator) {

                if (indicator.isCanceled()) return;
                indicator.setIndeterminate(false);
                //очистка store
                project.getService(GigaService.class).getTestSearcher().getStoreService().clear();
                project.getService(GigaService.class).getStepSearcher().getStoreService().clear();


                //сборка и обработки тестов
                List<TmsTest> allTest = TmsIndexUtil.getAllTests(project);
                if (!allTest.isEmpty()) {
                    log.info("Собрано %d автотестов".formatted(allTest.size()));
                    indicator.setText(MessagesUtils.get("indexing.autotests"));
                    indicator.setFraction(0.1);

                    if (indicator.isCanceled()) return;
                    project.getService(GigaService.class).getTestSearcher()
                            .cacheExistingTests(allTest, indicator);

                    project.getService(GigaService.class).getTestSearcher().getStoreService().saveStoreToFile();
                } else {
                    log.warn("Не найдено автотестов");
                }

                // сборка шагов
                indicator.setFraction(0.5);
                indicator.setText(MessagesUtils.get("indexing.finding.allure.steps"));
                if (indicator.isCanceled()) return;
                Set<AutoStep> steps = project.getService(GigaService.class).getJavaStepLoader().provideSteps(indicator);


                log.info("Собрано %d шагов".formatted(steps.size()));
                if (indicator.isCanceled()) return;
                // обработка шагов
                if (!steps.isEmpty()) {
                    indicator.setFraction(0.75);
                    indicator.setText(MessagesUtils.get("indexing.finding.result").formatted(steps.size()));
                    project.getService(GigaService.class).getStepSearcher().updateSteps(steps, indicator);

                    project.getService(GigaService.class).getStepSearcher().getStoreService().saveStoreToFile();

                } else {
                    log.warn("Шаги не найдены");
                }
                indicator.setFraction(1.0);

            }

            @Override
            public void onSuccess() {
                TmsMessage.message(MessagesUtils.get("indexing.completed.success"), NotificationType.INFORMATION);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                TmsMessage.message(MessagesUtils.get("indexing.completed.error") + ExceptionUtil.getRootCause(error), NotificationType.ERROR);
                log.error("Ошибка в процессе индексации: " + error);
            }

            @Override
            public void onCancel() {
                TmsMessage.message(MessagesUtils.get("indexing.stop"), NotificationType.WARNING);
                log.warn("Индексация остановлена");
            }
        }.queue(); // Автоматический запуск
    }
}
