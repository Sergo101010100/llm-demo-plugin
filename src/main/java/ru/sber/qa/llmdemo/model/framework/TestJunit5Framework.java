package ru.sber.qa.llmdemo.model.framework;

import com.intellij.openapi.project.Project;
import com.intellij.testIntegration.TestFramework;
import ru.sber.qa.llmdemo.index.TmsTest;

import java.util.List;

import static com.intellij.execution.junit.JUnitUtil.TEST5_ANNOTATION;

public class TestJunit5Framework extends AbstractJvmTestFramework {


    public TestJunit5Framework(Project project, TmsTest testCase) {
        super(project, testCase,
                TestFramework.EXTENSION_NAME
                        .findFirstSafe(f -> f.getName().equals("JUnit5") && !f.getLanguage().getID().equals("kotlin")).getIcon(),
                FrameworkName.Junit5);
    }

    @Override
    protected List<String> getFrameworkAnnotation() {
        //добавляем аннотацию теста
        List<String> annotations = new java.util.ArrayList<>();
        annotations.add("@" + TEST5_ANNOTATION);
        return annotations;
    }
}
