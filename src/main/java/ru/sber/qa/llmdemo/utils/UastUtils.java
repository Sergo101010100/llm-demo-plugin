package ru.sber.qa.llmdemo.utils;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastFacade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static com.intellij.execution.junit.JUnitUtil.TEST5_ANNOTATION;
import static ru.sber.qa.llmdemo.utils.PsiUtils.extractKeyFromText;
import static ru.sber.qa.llmdemo.utils.PsiUtils.getAttributeValue;

@UtilityClass
public class UastUtils {
    public static final String TEST5_PARAMETERIZED_ANNOTATION = "org.junit.jupiter.params.ParameterizedTest";
    public static final String TEST5_TEMPLATE = "org.junit.jupiter.api.TestTemplate";
    public static final String TEST5_REPEATED = "org.junit.jupiter.api.RepeatedTest";
    public static final String TEST5_FACTORY = "org.junit.jupiter.api.TestFactory";
    public static final String TEST4_ANNOTATION = "org.junit.Test";
    public static final String TEST_NG_ANNOTATION = "org.testng.annotations.Test";


    public static final String ALLURE_TMS_LINK = "io.qameta.allure.TmsLink";
    public static final String ALLURE_TMS_LINKS = "io.qameta.allure.TmsLinks";


    public static boolean isTest(@NotNull UMethod method) {
        return isTest(method.getJavaPsi());
    }

    public static boolean isTest(@NotNull PsiMethod method) {
        if (!method.isValid()) return false;
        if (DumbService.isDumb(method.getProject())) {
            for (PsiAnnotation annotation : method.getAnnotations()) {
                String text = PsiAnnotationImpl.getAnnotationShortName(annotation.getText());
                if (text.contains("Test")) {
                    return true;
                }
            }
            return false;
        } else {
            return method.hasAnnotation(TEST4_ANNOTATION)
                    || method.hasAnnotation(TEST5_ANNOTATION)
                    || method.hasAnnotation(TEST5_PARAMETERIZED_ANNOTATION)
                    || method.hasAnnotation(TEST_NG_ANNOTATION)
                    || method.hasAnnotation(TEST5_TEMPLATE)
                    || method.hasAnnotation(TEST5_REPEATED)
                    || method.hasAnnotation(TEST5_FACTORY);
        }
    }


    public static @Nullable UMethod findJvmTestByKey(Project project, String key) {
        return findJvmTestByKey(project, key, true);
    }

    public static @Nullable UMethod findJvmTestByKey(Project project, String key, boolean isKeyEqual) {
        return findJvmTestByKey(project, GlobalSearchScope.projectScope(project), key, isKeyEqual);
    }

    public static @Nullable UMethod findJvmTestByKey(Project project, GlobalSearchScope searchScope, String key, boolean isKeyEqual) {
        String fqnAllure = ALLURE_TMS_LINK;
        String fqnAllures = ALLURE_TMS_LINKS;
        Predicate<PsiMethod> filter = psiMethod -> {
                if (psiMethod.hasAnnotation(fqnAllure)) {
                    return isKeyEqual
                            ? getAttributeValue(psiMethod.getAnnotation(fqnAllure)
                            .findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)).equals(key)
                            : getAttributeValue(psiMethod.getAnnotation(fqnAllure)
                            .findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)).contains(key);
                } else if (psiMethod.hasAnnotation(fqnAllures)) {
                    Predicate<String> keyPredicate = isKeyEqual
                            ? x -> x.equals(key)
                            : x -> x.contains(key);
                    return Arrays.stream(psiMethod.getAnnotation(fqnAllures).findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME).getChildren())
                            .filter(x -> x instanceof PsiAnnotation)
                            .map(x -> getAttributeValue(((PsiAnnotation) x).findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)))
                            .anyMatch(keyPredicate);
                }
                return false;
            };
        return findJvmTest(project, searchScope, filter);
    }

    public static List<PsiClass> findJvmTestClass(Project project, GlobalSearchScope dependenciesScope) {

        List<PsiClass> result = new ArrayList<>();

        PsiClass annotationTest = findClassSafe(project, TEST5_ANNOTATION, dependenciesScope);
        if (annotationTest != null) result.add(annotationTest);

        PsiClass parameterizedTest = findClassSafe(project, TEST5_PARAMETERIZED_ANNOTATION, dependenciesScope);
        if (parameterizedTest != null) result.add(parameterizedTest);

        PsiClass testTemplate = findClassSafe(project, TEST5_TEMPLATE, dependenciesScope);
        if (testTemplate != null) result.add(testTemplate);

        PsiClass testRepeated = findClassSafe(project, TEST5_REPEATED, dependenciesScope);
        if (testRepeated != null) result.add(testRepeated);

        PsiClass testFactory = findClassSafe(project, TEST5_FACTORY, dependenciesScope);
        if (testFactory != null) result.add(testFactory);

        PsiClass annotation4Test = findClassSafe(project, TEST4_ANNOTATION, dependenciesScope);
        if (annotation4Test != null) result.add(annotation4Test);

        PsiClass annotationTestNgTest = findClassSafe(project, TEST_NG_ANNOTATION, dependenciesScope);
        if (annotationTestNgTest != null) result.add(annotationTestNgTest);

        return result;

    }

    // Вспомогательный метод для безопасного поиска классов
    private static PsiClass findClassSafe(Project project, String className, GlobalSearchScope scope) {
        return ReadAction.compute(() -> {
            try {
                return JavaPsiFacade.getInstance(project).findClass(className, scope);
            } catch (IndexNotReadyException e) {
                return null;
            }
        });
    }

    public static @Nullable UMethod findJvmTest(PsiFile psiFile, String key) {
        return findJvmTestByKey(psiFile.getProject(), GlobalSearchScope.fileScope(psiFile), key, true);
    }

    public static @Nullable UMethod findJvmTest(Project project, GlobalSearchScope
            searchScope, @NotNull Predicate<PsiMethod> filter) {
        AtomicReference<PsiMethod> result = new AtomicReference<>(null);


        List<PsiClass> annotations = SlowUtils.runSyncWithProgress(
                project,
                MessagesUtils.get("search.java.class"),
                () -> findJvmTestClass(project, GlobalSearchScope.allScope(project)));


        SlowUtils.runSyncWithProgress(project, MessagesUtils.get("search.java.test"), () -> {
            for (PsiClass psiClass : annotations) {
                PsiMethod method = ReadAction.compute(() ->
                        AnnotatedElementsSearch.searchPsiMethods(psiClass, searchScope)
                                .filtering(filter)
                                .findFirst()
                );

                if (method != null) {
                    result.set(method);
                    break;
                }
            }
            return null;
        });

        if (result.get() == null) {
            return null;
        }
        return (UMethod) UastFacade.INSTANCE.convertElementWithParent(result.get(), UMethod.class);
    }


    public static void addAnnotation(Project project, PsiElement test, @NotNull String fqnAnnotation,
                                     @NotNull String fieldName, @Nullable String value) {
        test.getLanguage().getDisplayName();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiAnnotation annotation = ((PsiMethod) test).getModifierList().addAnnotation(fqnAnnotation);
        if (value != null) {
            ReadAction.run(() -> {
                PsiExpression annotationValue = factory
                        .createExpressionFromText("\"%s\"".formatted(
                                        value.replaceAll("\"", "\\\\\"")),
                                test);
                annotation.setDeclaredAttributeValue(fieldName, annotationValue);
            });
        }
    }


    public static void addAnnotation(Project project, PsiElement test, @NotNull String fqnAnnotation, @Nullable String
            value) {
        addAnnotation(project, test, fqnAnnotation, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME, value);
    }


    public static @Nullable UMethod findJvmFirstTests(Project project, @NotNull GlobalSearchScope searchScope) {
        List<PsiClass> annotation = SlowUtils.runSyncWithProgress(project, MessagesUtils.get("search.java.class"),
                () -> UastUtils.findJvmTestClass(project, searchScope));
        return (UMethod) SlowUtils.runSyncWithProgress(project, MessagesUtils.get("search.java.test"), () -> {
            for (PsiClass psiClass : annotation) {
                PsiMethod test = AnnotatedElementsSearch.searchPsiMethods(psiClass, searchScope).findFirst();
                if (test != null) {
                    return UastFacade.INSTANCE.convertElementWithParent(test, UMethod.class);
                }
            }
            return null;
        });
    }

    public static List<String> getKeysTest(PsiMethod test) {
        List<String> keys = new ArrayList<>();
        if (test == null || !test.isValid()) {
            return keys;
        }

        Project project = test.getProject();
        boolean isDumbMode = DumbService.isDumb(project);


        if (isDumbMode) {
            String shortAllure = PsiAnnotationImpl.getAnnotationShortName(ALLURE_TMS_LINK);
            String shortAllures = PsiAnnotationImpl.getAnnotationShortName(ALLURE_TMS_LINKS);
            for (PsiAnnotation annotation : test.getAnnotations()) {
                String currentAn = PsiAnnotationImpl.getAnnotationShortName(annotation.getText());
                if (currentAn.equals(shortAllure) || currentAn.equals(shortAllures)) {
                    List<String> extracted = extractKeyFromText(annotation.getText());
                    keys.addAll(extracted);
                }
            }
        } else {
                String fqnAllure = ALLURE_TMS_LINK;
                String fqnAllures = ALLURE_TMS_LINKS;
                if (test.hasAnnotation(fqnAllure)) {
                    PsiAnnotation annotation = test.getAnnotation(fqnAllure);
                    if (annotation != null && annotation.isValid()) {
                        keys.add(getAttributeValue(annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)));
                    }
                } else if (test.hasAnnotation(fqnAllures)) {
                    PsiAnnotation annotation = test.getAnnotation(fqnAllures);
                    if (annotation != null && annotation.isValid()) {
                        PsiAnnotationMemberValue value = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
                        if (value != null) {
                            Arrays.stream(value.getChildren())
                                    .filter(x -> x instanceof PsiAnnotation)
                                    .map(x -> getAttributeValue(((PsiAnnotation) x).findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)))
                                    .forEach(keys::add);
                        }
                    }
                }

        }
        return keys;
    }
}
