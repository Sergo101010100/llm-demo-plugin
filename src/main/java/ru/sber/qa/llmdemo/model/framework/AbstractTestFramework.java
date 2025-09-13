package ru.sber.qa.llmdemo.model.framework;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testIntegration.createTest.CreateTestAction;
import com.intellij.testIntegration.createTest.CreateTestUtils;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UMethod;
import org.jsoup.Jsoup;
import ru.sber.qa.llmdemo.giga.GigaService;
import ru.sber.qa.llmdemo.index.TmsTest;
import ru.sber.qa.llmdemo.model.CreateTestDialog;
import ru.sber.qa.llmdemo.utils.MessagesUtils;
import ru.sber.qa.llmdemo.utils.SlowUtils;
import ru.sber.qa.llmdemo.utils.UastUtils;

import javax.swing.*;
import java.awt.*;
import java.util.List;


public abstract class AbstractTestFramework {
    protected static final Logger log = Logger.getInstance(AbstractTestFramework.class);
    protected final static String allureStepQuetion = "Добавить шаги сценария в виде Allure step?";
    protected final static String gigaQuestion = "[Beta] Сгенерировать тест с использованием Gigachat?";
    @Getter
    protected final Project project;

    protected Module currentModule;
    @Getter
    private final Icon icon;
    @Getter
    protected final FrameworkName frameworkName;
    protected final TmsTest testCase;
    protected JPanel contentPane;

    protected JBRadioButton aiCheckbox;
    protected final JBLabel warningArea;

    @Getter
    protected GigaService gigaService;

    @Getter
    public ClassNameAutocomplete myTargetClassNameField;

    public AbstractTestFramework(Project project, TmsTest testCase,
                                 Icon icon, FrameworkName frameworkName,
                                 GigaService gigaService) {
        this.icon = icon;
        this.frameworkName = frameworkName;
        this.project = project;
        this.testCase = testCase;
        this.gigaService = gigaService;
        this.warningArea = new JBLabel();
        this.aiCheckbox = new JBRadioButton();

    }

    /**
     * Создание нового автотесте
     */
    public abstract @Nullable PsiElement generateTest(String codeTest);

    public abstract String generateTestCode(ProgressIndicator indicator);

    public abstract PsiElement getTarget();

    public abstract boolean isAiTestGenerated();

    /*
    создание панели для параметров при создании автотеста
     */
    public abstract JComponent getPanel(CreateTestDialog dialog);

    protected Dimension getPanelDimension() {
        return new Dimension(200, 60);
    }

    protected String getTextFromHtml(String html) {
        if (html == null) {
            return "";
        }
        String text = Jsoup.parse(html).text().replaceAll("\\p{Cf}", " ");
        StringBuilder stringBuilder = new StringBuilder();
        String[] lines = text.split("\n", -1); // Разбиваем текст на строки с сохранением пустых

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            int i = 0;

            // Обрабатываем каждую строку отдельно
            while (i < line.length()) {
                // Добавляем # в начало каждой новой строки
                stringBuilder.append("#");

                // Определяем конец текущей части (максимум 100 символов)
                int end = Math.min(i + 100, line.length());

                // Если не в конце строки, ищем ближайший пробел для разрыва
                if (end < line.length()) {
                    int spacePos = end;
                    while (spacePos > i && line.charAt(spacePos) != ' ') {
                        spacePos--;
                    }
                    if (spacePos > i) {
                        end = spacePos;
                    }
                }

                // Добавляем часть текста
                stringBuilder.append(line, i, end);

                // Переходим к следующей части
                i = end;
                if (i < line.length() && line.charAt(i) == ' ') {
                    i++;
                }

                // Добавляем перенос строки, если это не конец
                if (i < line.length()) {
                    stringBuilder.append("\n");
                }
            }

            // Добавляем перенос между исходными строками (кроме последней)
            if (lineIndex < lines.length - 1) {
                stringBuilder.append("\n");
            }
        }
        return stringBuilder.toString();
    }

    /**
     * Определение базового пакета для тестов в модуле
     *
     * @param currentModule модуль
     * @return
     */
    protected String getTargetPackage(Module currentModule) {
        GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);
        java.util.List<PsiClass> junit = SlowUtils.runSyncWithProgress(project, MessagesUtils.get("search.java.class"),
                () -> UastUtils.findJvmTestClass(project, searchScope));
        if (!junit.isEmpty()) {
            List<VirtualFile> tests = CreateTestUtils.computeTestRoots(
                    CreateTestAction.suggestModuleForTests(project, currentModule));
            if (!tests.isEmpty()) {
                //ищем первую папку с тестами
                PsiDirectory testDirectory = SlowUtils.runSyncWithProgress(project, MessagesUtils.get("search.test.package"),
                        () -> PsiManager.getInstance(project).findDirectory(tests.getFirst()));
                if (testDirectory != null) {
                    //пробуем найти первый Test в проекте
                    UMethod firstTest = UastUtils.findJvmFirstTests(project, SlowUtils.runSyncWithProgress(project, MessagesUtils.get("search.test.package"),
                            testDirectory::getResolveScope));
                    //по найденному тесту находим родительский класс и берем его пакет
                    if (firstTest != null) {
                        PsiPackage testPackage = SlowUtils.runSyncWithProgress(project, MessagesUtils.get("search.java.test"),
                                () -> JavaDirectoryService.getInstance().getPackage(firstTest.getContainingFile().getContainingDirectory()));
                        if (testPackage != null) {
                            return testPackage.getQualifiedName();
                        }
                    }
                }
            }
        }
        return ProjectUtil.guessModuleDir(currentModule).getPath();
    }

    public abstract void updateTest(String refinedCode);
}
