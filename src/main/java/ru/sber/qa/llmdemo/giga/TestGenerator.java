package ru.sber.qa.llmdemo.giga;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.psi.PsiElement;
import com.intellij.util.ExceptionUtil;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.sber.qa.llmdemo.index.TmsTest;
import ru.sber.qa.llmdemo.model.framework.AbstractTestFramework;
import ru.sber.qa.llmdemo.utils.TmsMessage;

@Getter
public class TestGenerator {
    private final AbstractTestFramework framework;
    private String codeTest;
    private final TmsTest tmsTest;

    public TestGenerator(AbstractTestFramework framework, @NotNull TmsTest tmsTest) {
        this.framework = framework;
        this.tmsTest = tmsTest;
    }

    public void generateTestBody() {
        Task.WithResult<String, RuntimeException> task = new Task.WithResult<>(framework.getProject(), "Генерация Автотеста", true) {
            @Override
            protected String compute(@NotNull ProgressIndicator indicator) throws RuntimeException {
                indicator.setIndeterminate(false);
                return framework.generateTestCode(indicator);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                TmsMessage.message("Ошибка при генерации теста: " + ExceptionUtil.getRootCause(error), NotificationType.ERROR);
                super.onThrowable(error);
            }
        };
        codeTest = ProgressManager.getInstance().run(task);
    }

    public void generateTestBody(ProgressIndicator indicator) {
        codeTest = framework.generateTestCode(indicator);
    }
    public @Nullable PsiElement generateTestPsiElement() {
        return framework.generateTest(codeTest);
    }

    public @Nullable PsiElement generateTestElement() {
        if (codeTest == null) generateTestBody();
        return framework.generateTest(codeTest);
    }
}
