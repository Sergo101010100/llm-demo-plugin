package ru.sber.qa.llmdemo.model.framework;

import com.intellij.java.JavaBundle;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.testIntegration.createTest.CreateTestAction;
import com.intellij.testIntegration.createTest.CreateTestUtils;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.Function;
import com.intellij.util.ui.FormBuilder;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastFacade;
import org.jsoup.Jsoup;
import ru.sber.qa.llmdemo.dto.Step;
import ru.sber.qa.llmdemo.giga.GigaService;
import ru.sber.qa.llmdemo.giga.JavaTestGenerator;
import ru.sber.qa.llmdemo.giga.JvmTestGenerator;
import ru.sber.qa.llmdemo.index.TestLanguage;
import ru.sber.qa.llmdemo.index.TmsIndexUtil;
import ru.sber.qa.llmdemo.index.TmsTest;
import ru.sber.qa.llmdemo.model.CreateTestDialog;
import ru.sber.qa.llmdemo.utils.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.intellij.testIntegration.createTest.CreateTestUtils.selectTargetDirectory;
import static ru.sber.qa.llmdemo.utils.UastUtils.ALLURE_TMS_LINK;

public abstract class AbstractJvmTestFramework extends AbstractTestFramework {
    private TestPackageNameReferenceEditorCombo myTargetPackageField;
    protected PsiDirectory targetDirectory;
    protected final ButtonGroup optionsButtonGroup = new ButtonGroup();
    protected JBRadioButton stepCheckbox = new JBRadioButton();

    protected ModulesComboBox modules;
    protected Module currentModule;

    @Getter
    private UClass targetClass;

    private final List<Language> optionsLanguage = new LinkedList<>();
    @Setter
    private JvmTestGenerator jvmTestGenerator;

    public AbstractJvmTestFramework(Project project, TmsTest testCase, Icon icon, FrameworkName name) {
        super(project, testCase, icon, name, project.getService(GigaService.class));
        optionsButtonGroup.add(stepCheckbox);
        optionsButtonGroup.add(aiCheckbox);
        stepCheckbox.setSelected(true);

        optionsLanguage.add(JavaLanguage.INSTANCE);

    }

    @Override
    public JComponent getPanel(CreateTestDialog dialog) {
        FormBuilder builder = FormBuilder.createFormBuilder();

        JPanel optionsPanel = new JPanel(new GridLayout(2, 2));

        //попытка определить пакет с тестами
        AtomicReference<String> targetPackageName = new AtomicReference<>("");

        //если модулей много - предлагаем выбрать
        setupModuleChoice(builder, targetPackageName);

        myTargetPackageField = new TestPackageNameReferenceEditorCombo(targetPackageName.get(), currentModule,
                JavaBundle.message("dialog.create.class.package.chooser.title"));

        new AnAction() {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                myTargetPackageField.getButton().doClick();
            }
        }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)),
                myTargetPackageField.getChildComponent());


        myTargetClassNameField = new ClassNameAutocomplete(currentModule, getPackageName(), TestLanguage.JAVA);

        // Подписываемся на изменение пакета
        myTargetPackageField.addPackageChangeListener(newPackage ->
                myTargetClassNameField.setPackageName(currentModule, newPackage, TestLanguage.JAVA));

        optionsPanel.add(new JBLabel("Наименование пакета"));
        optionsPanel.add(myTargetPackageField);
        optionsPanel.add(new JBLabel("Наименование класса"));
        optionsPanel.add(myTargetClassNameField);

        optionsPanel.setPreferredSize(getPanelDimension());

        builder.addComponent(optionsPanel);

        //флаги будет только если есть не пустые шаги

        JPanel checkboxPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        checkboxPanel.add(new JLabel(allureStepQuetion), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        checkboxPanel.add(stepCheckbox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        checkboxPanel.add(new JLabel(gigaQuestion), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        checkboxPanel.add(aiCheckbox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        checkboxPanel.add(warningArea, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        checkboxPanel.add(Box.createHorizontalGlue(), gbc);

        builder.addComponent(checkboxPanel);

        contentPane = builder.getPanel();
        return contentPane;
    }

    @Override
    public void updateTest(String refinedCode) {
        JVMElementFactory factory = JVMElementFactories.getFactory(jvmTestGenerator.getLanguageId(), project);
        if (factory == null) {
            factory = JavaPsiFacade.getElementFactory(project);
        }
        PsiElement newMethod = factory.createMethodFromText(refinedCode, null);
        WriteCommandAction.runWriteCommandAction(project,
                () -> {
                    PsiElement test = testCase.getTestProject(project);
                    PsiFile testFile = test.getContainingFile();

                    test.replace(newMethod);

                    // Принудительно обновляем VFS и PSI
                    testFile.getVirtualFile().refresh(false, false);
                    PsiDocumentManager.getInstance(project).commitAllDocuments();

                    Optional.ofNullable(UastUtils.findJvmTest(testFile, testCase.getKey()))
                            .map(UElement::getSourcePsi)
                            .ifPresent(testCase::setTestProject);

                });
    }

    protected void setupModuleChoice(FormBuilder builder, AtomicReference<String> targetPackageName) {
        //если модулей много - предлагаем выбрать
        java.util.List<Module> allModules = Stream.of(ModuleManager.getInstance(project).getModules())
                .filter(ModulesComboBox::isModuleWithTests).toList();
        if (allModules.size() > 1) {
            modules = new ModulesComboBox(allModules);
            modules.setSelectedIndex(0);
            builder.addLabeledComponent("Наименование модуля", modules, 1, false);

            //при выборе друго модуля - меняем базовый пакет
            addModuleChangeListener(targetPackageName);
            currentModule = ModuleManager.getInstance(project).findModuleByName((String) modules.getSelectedItem());
        } else if (allModules.size() == 1) {
            //если модуль один
            currentModule = ModuleManager.getInstance(project).findModuleByName(allModules.getFirst().getName());
        } else {
            //если модулей с тестами нет
            currentModule = ModuleManager.getInstance(project).getModules()[0];
        }
        //если не нашли модули по имени выше - тоже берем первый
        if (currentModule == null) {
            currentModule = ModuleManager.getInstance(project).getModules()[0];
        }
        targetPackageName.set(getTargetPackage(currentModule));
    }

    protected void addModuleChangeListener(AtomicReference<String> targetPackageName) {
        modules.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED && myTargetPackageField != null) {
                Module selectedModule = ModuleManager.getInstance(project).findModuleByName((String) modules.getSelectedItem());
                if (selectedModule != null) {
                    currentModule = selectedModule;
                    targetPackageName.set(getTargetPackage(currentModule));

                    String newTargetPackageName = getTargetPackage(currentModule);
                    ((JPanel) contentPane.getComponents()[2]).remove(1);
                    myTargetPackageField = new TestPackageNameReferenceEditorCombo(newTargetPackageName, currentModule,
                            JavaBundle.message("dialog.create.class.package.chooser.title"));
                    //пересоздаем на том же месте
                    ((JPanel) contentPane.getComponents()[2]).add(myTargetPackageField, 1);
                    contentPane.repaint();

                    //так же обновляем список классов по новому пакету
                    myTargetClassNameField.setPackageName(currentModule, newTargetPackageName, TestLanguage.JAVA);
                    // И пересоздаем слушателя  изменение пакета
                    myTargetPackageField.addPackageChangeListener(newPackage ->
                            myTargetClassNameField.setPackageName(currentModule, newPackage, TestLanguage.JAVA));
                }
            }
        });

    }

    /**
     * Определение базового пакета для тестов в модуле
     *
     * @param currentModule модуль
     * @return
     */
    protected String getTargetPackage(Module currentModule) {
        List<VirtualFile> testRoots = CreateTestUtils.computeTestRoots(
                CreateTestAction.suggestModuleForTests(project, currentModule));

        if (!testRoots.isEmpty()) {
            return findFirstTestPackage(project, testRoots.getFirst());
        }
        TmsTest firstTest = TmsIndexUtil.getAllTests(project).stream()
                .filter(t -> t.getTestLanguage() == TestLanguage.JAVA)
                .findFirst().orElse(null);
        if (firstTest != null) {
            PsiPackage testPackage = SlowUtils.runSyncWithProgress(project, MessagesUtils.get("search.java.test"),
                    () -> JavaDirectoryService.getInstance().getPackage(firstTest.getTestProject(project).getContainingFile().getContainingDirectory()));
            if (testPackage != null) {
                return testPackage.getQualifiedName();
            }
        }
        return "";
    }

    /**
     * Находит первый тестовый пакет через сканирование директории.
     */
    private String findFirstTestPackage(Project project, VirtualFile testRoot) {

        PsiDirectory testDir = SlowUtils.runSyncWithProgress(project, "Search test package",
                () -> PsiManager.getInstance(project).findDirectory(testRoot));
        if (testDir == null) return "";

        UMethod firstTest = UastUtils.findJvmFirstTests(project, SlowUtils.runSyncWithProgress(project, MessagesUtils.get("search.test.package"),
                testDir::getResolveScope));
        if (firstTest != null) {
            AtomicReference<String> packageName = new AtomicReference<>("");
            if (firstTest.getSourcePsi().getContainingFile() instanceof PsiJavaFileImpl psiJavaFile) {
                packageName.set(psiJavaFile.getPackageName());
            }
            return packageName.get();
        }
        return "";

    }

    /**
     * MAGIC
     * основано на {@link com.intellij.testIntegration.createTest.CreateTestDialog}
     */
    @Override
    public @Nullable PsiElement generateTest(String codeTest) {
        calcLanguage();
        UMethod fullTest = jvmTestGenerator.createFullTestMethod(codeTest, testCase.getKey(), jvmTestGenerator.getLanguageId(),
                Optional.ofNullable(targetClass).map(UClass::getSourcePsi).orElse(null));

        PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(
                () -> WriteCommandAction.runWriteCommandAction(project, () -> {
                            IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
                            if (targetClass == null) {
                                //иначе создаем класс
                                targetClass = jvmTestGenerator.createTestClass(targetDirectory, getClassName());
                            }
                            if (targetClass == null) {
                                TmsMessage.message("Не удалось создать класс для создания теста", NotificationType.ERROR);
                                return;
                            }
                            jvmTestGenerator.addTestMethodInClass(fullTest, targetClass);
                        }
                )
        );
        //ищем и возвращаем добавленный тест в класс
        // который мы создали не подойдет, так как это другой объект
        Optional<UMethod> tmsTest = Optional.ofNullable(UastUtils.findJvmTestByKey(project, testCase.getKey()));
        return tmsTest.map(UElement::getSourcePsi).orElse(null);
    }


    @Override
    public String generateTestCode(ProgressIndicator indicator) {
        PsiElement targetClass = getTarget();
        calcLanguage();
        List<String> annotations = new ArrayList<>(getFrameworkAnnotation());
        annotations.add("@" + ALLURE_TMS_LINK + "(\"" + testCase.getKey() + "\")");

        if (!isAiTestGenerated()) {
            return getAllureStepsFromZephyr(annotations);
        }
        String targetClassText = targetClass == null ? null : ReadAction.compute(targetClass::getText);

        String aiResponse = project.getService(GigaService.class)
                .generateStep(testCase, targetClassText, annotations, indicator);
        return MdUtils.getCodeBlock(aiResponse);
    }

    @Override
    public boolean isAiTestGenerated() {
        if (optionsButtonGroup.isSelected(stepCheckbox.getModel())) {
            return false;
        }
        return optionsButtonGroup.isSelected(aiCheckbox.getModel());
    }

    @Override
    public @Nullable PsiElement getTarget() {
        if (targetClass != null) return targetClass.getSourcePsi();
        targetClass = getTestClass();
        if (targetClass == null) return null;
        return targetClass.getSourcePsi();
    }

    private void calcLanguage() {
        //по умолчанию выбран Java

        if (jvmTestGenerator == null) {
            jvmTestGenerator = new JavaTestGenerator(project);

        }
    }

    protected abstract List<String> getFrameworkAnnotation();

    /**
     * Создает класс, если он не существует
     *
     * @return
     */
    protected @Nullable UClass getTestClass() {
        ApplicationManager.getApplication().invokeAndWait(
                () -> targetDirectory = selectTargetDirectory(getPackageName(), project, currentModule));

        return SlowUtils.runSyncWithProgress(project, "", () -> {
            final PsiPackage aPackage =
                    JavaDirectoryService.getInstance().getPackage(targetDirectory);
            if (aPackage != null) {
                final GlobalSearchScope scope = GlobalSearchScopesCore.directoryScope(targetDirectory, true);

                final PsiClass[] classes = aPackage.findClassByShortName(getClassName(), scope);
                //если класс уже существует, то не создаем его
                if (classes.length > 0) {
                    AtomicReference<UClass> resultClass = new AtomicReference<>();

                    //если Kotlin не включен, то класс может быть только Java
                    if (resultClass.get() == null) {
                        resultClass.set(ReadAction.compute(() -> (UClass) UastFacade.INSTANCE.convertElementWithParent(classes[0], UClass.class)));
                    }
                    return resultClass.get();
                }

            }
            return null;
        });
    }

    protected String getClassName() {
        return (String) myTargetClassNameField.getSelectedItem();
    }

    protected String getPackageName() {
        String name = myTargetPackageField.getText();
        return name != null ? name.trim() : "";
    }

    private String getAllureStepsFromZephyr(List<String> annotations) {
        var jira = testCase.getTest();
        var script = (jira != null) ? jira.getTestScript() : null;
        List<Step> steps = (script != null && script.getSteps() != null)
                ? script.getSteps()
                : java.util.List.of();

        return buildAllureSteps(
                annotations,
                steps,
                Step::getDescription,
                Step::getTestData,
                Step::getExpectedResult
        );
    }

    private <S> String buildAllureSteps(List<String> annotations,
                                        List<S> steps,
                                        Function<S, String> actionFn,
                                        Function<S, String> dataFn,
                                        Function<S, String> expectedFn) {
        StringBuilder stepString = beginTestBody(annotations);
        for (int i = 0; i < steps.size(); i++) {
            S s = steps.get(i);
            stepString.append(jvmTestGenerator.getAllureStepPattern().formatted(
                    i + 1,
                    formatTextStep(null, actionFn.apply(s)),
                    formatTextStep("Тестовые данные: ", dataFn.apply(s)),
                    formatTextStep("Ожидаемый результат: ", expectedFn.apply(s))
            ));
        }
        stepString.append("\n}");
        return stepString.toString();
    }

    private StringBuilder beginTestBody(List<String> annotations) {
        StringBuilder stepString = new StringBuilder();
        annotations.forEach(annotation ->
                stepString.append(annotation).append("\n"));
        String methodSufix = testCase.getKey().replace("-", "_");
        stepString.append("public void test_").append(methodSufix).append("() {\n");
        return stepString;
    }

    /**
     * Преобразует текст шага к нормальному виду
     * Если строка длиннее 100 символов - разбивает на несколько строк с переносами
     * Если есть комментарий - первая строка начинается с // + комментарий, остальные с //
     * Если комментария нет - просто возвращает текст с экранированием спецсимволов
     *
     * @param comment комментарий к шагу (может быть null)
     * @param text    текст шага
     * @return преобразованный текст шага
     */
    private String formatTextStep(@Nullable String comment, String text) {
        if (text == null || text.isEmpty()) {
            return "\n";
        }

        // Очищаем текст от HTML и неразрывных пробелов
        String textFromStep = Jsoup.parse(text).text().replaceAll("\\p{Cf}", " ");

        StringBuilder result = new StringBuilder();

        if (comment != null) {
            // Разбиваем текст на строки по переносам
            String[] lines = textFromStep.split("\n", -1);
            boolean isFirstLine = true;

            for (String line : lines) {
                int pos = 0;
                boolean isFirstSegment = true;

                while (pos < line.length()) {
                    // Добавляем начало строки
                    if (isFirstLine && isFirstSegment) {
                        result.append("//").append(comment).append(" ");
                        isFirstSegment = false;
                    } else {
                        result.append("// ");
                    }

                    // Определяем конец текущего фрагмента (максимум 100 символов)
                    int end = Math.min(pos + 100, line.length());

                    // Ищем ближайший пробел для разрыва (если не конец строки)
                    if (end < line.length()) {
                        int spacePos = end;
                        while (spacePos > pos && line.charAt(spacePos) != ' ') {
                            spacePos--;
                        }
                        if (spacePos > pos) {
                            end = spacePos;
                        }
                    }

                    // Добавляем часть текста
                    result.append(line, pos, end);

                    // Переходим к следующей части
                    pos = end;
                    if (pos < line.length() && line.charAt(pos) == ' ') {
                        pos++;
                    }

                    // Добавляем перенос строки, если это не конец
                    if (pos < line.length()) {
                        result.append("\n");
                    }
                }

                isFirstLine = false;
                // Добавляем перенос между оригинальными строками
                if (!line.isEmpty()) {
                    result.append("\n");
                }
            }

            // Удаляем последний лишний перенос строки
            return !result.isEmpty() ? result.substring(0, result.length() - 1) : "";
        } else {
            // Без комментария - просто экранируем спецсимволы
            return textFromStep.replace("\\", "\\\\")
                    .replace("\"", "\\\"");
        }
    }
}
