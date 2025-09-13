package ru.sber.qa.llmdemo.giga;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastFacade;

public class JavaTestGenerator extends JvmTestGenerator {

    public JavaTestGenerator(Project project) {
        super(project,"JAVA");
    }

    @Override
    public UClass getNewClass(PsiDirectory targetDirectory, String testClassName) {
        PsiElement newTestClass = ApplicationManager.getApplication().runReadAction((Computable<PsiClass>)
                () -> JavaDirectoryService.getInstance().createClass(targetDirectory, testClassName));
        return (UClass) UastFacade.INSTANCE.convertElementWithParent(newTestClass, UClass.class);
    }

    @Override
    public void addTestMethod(UMethod testMethod, UClass targetClass) {
        PsiElement shortedTest = JavaCodeStyleManager.getInstance(project).shortenClassReferences(testMethod.getJavaPsi());
        targetClass.getSourcePsi().add(shortedTest);
    }

    @Override
    public String getAllureStepPattern() {
        return """
                //step №%s
                io.qameta.allure.Allure.step(
                \"""
                %s
                \""",()->{
                %s
                %s
                });
                \n
                """;
    }

}
