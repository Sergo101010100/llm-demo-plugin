package ru.sber.qa.llmdemo.model;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import ru.sber.qa.llmdemo.giga.TestGenerator;
import ru.sber.qa.llmdemo.giga.TmsTestLLMToolWindow;
import ru.sber.qa.llmdemo.index.TmsTest;
import ru.sber.qa.llmdemo.utils.SlowUtils;
import ru.sber.qa.llmdemo.utils.TmsMessage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class AutotestButton extends JButton {
    protected static final Logger log = Logger.getInstance(AutotestButton.class);
    private final TmsTest tmsTest;
    private ActionListener create;
    private final Project project;

    public AutotestButton(Project project, TmsTest tmsTest) {
        super();
        this.tmsTest = tmsTest;
        this.project = project;
        setPreferredSize(new Dimension(180, 30));

        PsiElement testProject = SlowUtils.runSyncWithProgress(project, "Search test",
                () -> tmsTest.getTestProject(project));
        if (testProject == null) {
            getCreateTest(project);
        } else {
            getOpenTest();
        }
    }

    private void getOpenTest() {
        this.setText("Открыть авто тест");
        if (create != null) {
            this.removeActionListener(create);
            openTest();
        }
        this.addActionListener(l -> openTest());
    }

    private void openTest() {
        UIUtil.invokeLaterIfNeeded(() -> {
            if (tmsTest.getTestProject(project) != null) {
                VirtualFile file = tmsTest.getTestProject(project).getContainingFile().getVirtualFile();
                if (file != null && file.isValid()) {
                    int offset = tmsTest.getTestProject(project).getTextOffset();
                    FileEditorManager.getInstance(tmsTest.getTestProject(project).getProject()).openTextEditor(
                            new OpenFileDescriptor(tmsTest.getTestProject(project).getProject(), file, offset),
                            true
                    );
                }
            }
        });
    }

    private void getCreateTest(Project project) {
        this.setText("Создать авто тест");
        create = l -> createTest(project);
        this.addActionListener(create);
    }

    private void createTest(Project project) {
        if (tmsTest.getTestProject(project) == null) {
                CreateTestDialog dialog = new CreateTestDialog(project, tmsTest);
            if (dialog.showAndGet()) {
                if (dialog.getGeneratedTest() != null) {
                    tmsTest.setTestProject(dialog.getGeneratedTest());
                    getOpenTest();
                    return;
                }
            }
            if (dialog.isAiGenerationSelected()) {
                Task.Backgroundable backGenerationTask = new Task.Backgroundable(project, "Генерация автотеста", true) {
                    final TestGenerator testGenerator = new TestGenerator(dialog.getSelectedFramework(), tmsTest);

                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        indicator.setIndeterminate(false);
                        testGenerator.generateTestBody(indicator);
                        indicator.setFraction(1);
                    }

                    @Override
                    public void onSuccess() {
                        PsiElement generatedTest = testGenerator.generateTestPsiElement();
                        if (generatedTest != null) {
                            tmsTest.setTestProject(generatedTest);
                        } else {
                            log.error("Ошибка при создании автотеста: generatedTest is null");
                            Messages.showErrorDialog("Что-то пошло не так при создании автотеста", "Error");
                        }
                        getOpenTest();
                        TmsTestLLMToolWindow.showChat(project, testGenerator);
                    }

                    @Override
                    public void onThrowable(@NotNull Throwable error) {
                        TmsMessage.message("Ошибка при генерации теста: " + ExceptionUtil.getRootCause(error), NotificationType.ERROR);
                        super.onThrowable(error);
                    }
                };
                backGenerationTask.queue();
            }
        }
    }
}
