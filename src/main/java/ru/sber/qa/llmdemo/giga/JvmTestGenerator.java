package ru.sber.qa.llmdemo.giga;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.lang.Language;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.util.ExceptionUtil;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;
import ru.sber.qa.llmdemo.utils.SlowUtils;
import ru.sber.qa.llmdemo.utils.TmsMessage;

import static ru.sber.qa.llmdemo.utils.PsiUtils.getAttributeValue;
import static ru.sber.qa.llmdemo.utils.UastUtils.ALLURE_TMS_LINK;
import static ru.sber.qa.llmdemo.utils.UastUtils.addAnnotation;

public abstract class JvmTestGenerator {
    protected static final Logger log = Logger.getInstance(JvmTestGenerator.class);
    protected final Project project;
    @Getter
    protected Language languageId;

    public JvmTestGenerator(Project project, String languageId) {
        this.project = project;
        this.languageId = Language.findLanguageByID(languageId);
    }

    public UMethod createFullTestMethod(String fullCodeTest, String key, Language language, @Nullable PsiElement context) {
        UMethod method = UastCodeGenerationPlugin.byLanguage(language)
                .getElementFactory(project)
                .createMethodFromText(fullCodeTest, context);
        if (method == null) return null;
        //проверяем что ключ корректный - иногда ллм вставляет из примера
        String tmsAnnotation = ALLURE_TMS_LINK;
        PsiAnnotation annotation = SlowUtils.runSyncWithProgress(project, "",
                () -> method.getJavaPsi().getAnnotation(tmsAnnotation));
        if (annotation == null) {
            SlowUtils.runSyncWithProgress(project, "", () -> addAnnotation(project, method.getJavaPsi(), tmsAnnotation, key));
        } else {
            PsiAnnotationMemberValue tmsKeyValue = annotation
                    .findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
            String tmsKey = getAttributeValue(tmsKeyValue);
            if (!tmsKey.equals(key)) {
                annotation.delete();
                SlowUtils.runSyncWithProgress(project, "", () -> addAnnotation(project, method.getJavaPsi(), tmsAnnotation, key));
            }
        }
        return method;
    }

    public @Nullable UClass createTestClass(PsiDirectory targetDirectory, String testClassName) {
        String errorMessage = ReadAction.compute(() ->
                RefactoringMessageUtil.checkCanCreateClass(targetDirectory, testClassName));
        if (errorMessage != null) {
            TmsMessage.message(errorMessage, NotificationType.ERROR);
            return null;
        }
        return getNewClass(targetDirectory, testClassName);
    }

    protected abstract UClass getNewClass(PsiDirectory targetDirectory, String testClassName);

    public void addTestMethodInClass(UMethod testMethod, UClass targetClass) {
        PsiFile file = targetClass.getJavaPsi().getContainingFile();
        Editor editor = CodeInsightUtil.positionCursor(project, file, targetClass.getJavaPsi());

        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                addTestMethod(testMethod, targetClass);
                if (editor != null) {
                    PsiDocumentManager.getInstance(targetClass.getJavaPsi().getProject())
                            .doPostponedOperationsAndUnblockDocument(editor.getDocument());
                }
            } catch (Exception e) {
                TmsMessage.message("При генерации автотеста возникла ошибка " + ExceptionUtil.getRootCause(e), NotificationType.ERROR);
                log.info("При генерации автотеста возникла ошибка\n" + e);
            }
        });
    }

    public abstract void addTestMethod(UMethod testMethod, UClass targetClass);

    public abstract String getAllureStepPattern();

}
