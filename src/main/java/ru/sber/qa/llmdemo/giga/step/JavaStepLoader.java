package ru.sber.qa.llmdemo.giga.step;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaStepLoader {
    private static final com.intellij.openapi.diagnostic.Logger log = Logger.getInstance(JavaStepLoader.class);
    private final Project project;
    private final AtomicBoolean isDisposed = new AtomicBoolean(false);

    public JavaStepLoader(Project project) {
        this.project = project;
    }

    public void dispose() {
        isDisposed.set(true);
    }

    private boolean checkValid() {
        return isDisposed.get() || !project.isOpen() || project.isDisposed();
    }

    public Set<AutoStep> provideSteps(@Nullable ProgressIndicator indicator) {
        Set<AutoStep> steps = new HashSet<>();
        if (checkValid()) return steps;

        // Ищем все методы с аннотацией @Step
        steps.addAll(findAnnotatedSteps(indicator));

        // Ищем все вызовы Allure.step()
        steps.addAll(findAllureStepCalls(indicator));

        indicator.setText("Поиск allure шагов...");
        indicator.setText2(null);

        log.info("Загружено " + steps.size() + " allure шагов");
        return steps;
    }

    private Set<AutoStep> findAnnotatedSteps(@Nullable ProgressIndicator indicator) {
        Set<AutoStep> steps = new HashSet<>();

        PsiClass stepAnnotation = ReadAction.compute(() ->
                JavaPsiFacade.getInstance(project)
                        .findClass("io.qameta.allure.Step", GlobalSearchScope.allScope(project))
        );

        if (stepAnnotation == null) {
            log.info("Не найдена аннотация @Step");
            return steps;
        }

        Query<PsiMethod> methods = AnnotatedElementsSearch.searchPsiMethods(
                stepAnnotation,
                GlobalSearchScope.allScope(project)
        );

        int processed = 0;
        for (PsiMethod method : methods) {
            if (indicator != null && indicator.isCanceled()) break;
            if (checkValid()) break;
            if (processed++ > 300) break; // Лимит на обработку

            AutoStep step = createStepFromAnnotatedMethod(method);
            if (step != null) {
                steps.add(step);
            }

            pauseForCancellation(20, indicator);
        }
        log.info("Найдено " + steps.size() + " аннотированных шагов");
        return steps;
    }

    private Set<AutoStep> findAllureStepCalls(@Nullable ProgressIndicator indicator) {
        Set<AutoStep> steps = new HashSet<>();

        Collection<PsiMethodCallExpression> calls = ReadAction.compute(() -> {
            if (checkValid()) return Collections.emptyList();

            List<PsiMethodCallExpression> result = new ArrayList<>();
            ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);

            fileIndex.iterateContent(file -> {
                PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(project).findFile(file));
                if (psiFile instanceof PsiJavaFile psiJavaFile) {
                    psiJavaFile.accept(new JavaRecursiveElementVisitor() {
                        @Override
                        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                            if (isAllureStepMethodCall(expression)) {
                                result.add(expression);
                            }
                            super.visitMethodCallExpression(expression);
                        }
                    });
                }
                return true;
            });

            return result;
        });

        int processed = 0;
        for (PsiMethodCallExpression call : calls) {
            if (indicator != null && indicator.isCanceled()) break;
            if (checkValid()) break;
            if (processed++ > 200) break; // Лимит на обработку

            AutoStep step = createStepFromMethodCall(call, indicator);
            if (step != null) {
                steps.add(step);
            }

            pauseForCancellation(10, indicator);
        }
        log.info("Найдено " + steps.size() + " вызовов Allure.step()");
        return steps;
    }

    private boolean isAllureStepCall(PsiElement element) {
        if (element instanceof PsiExpressionStatement) {
            PsiExpression expr = ((PsiExpressionStatement) element).getExpression();
            if (expr instanceof PsiMethodCallExpression call) {
                return isAllureStepMethodCall(call);
            }
        }
        return false;
    }

    private boolean isAllureStepMethodCall(PsiMethodCallExpression expression) {
        // Проверяем обычный вызов Allure.step()
        PsiReferenceExpression methodExpr = expression.getMethodExpression();

        // Случай 1: Явный вызов Allure.step()
        if (methodExpr.getQualifierExpression() != null &&
                "Allure".equals(methodExpr.getQualifierExpression().getText()) &&
                "step".equals(methodExpr.getReferenceName())) {
            return true;
        }

        // Случай 2: Статический импорт (просто step())
        if (methodExpr.getQualifierExpression() == null) {
            PsiMethod method = expression.resolveMethod();
            if (method != null) {
                PsiClass containingClass = method.getContainingClass();
                return containingClass != null &&
                        "io.qameta.allure.Allure".equals(containingClass.getQualifiedName()) &&
                        "step".equals(method.getName());
            }
        }

        return false;
    }

    private AutoStep createStepFromAnnotatedMethod(PsiMethod method) {
        return ReadAction.compute(() -> {
            if (checkValid() || !method.isValid()) return null;

            PsiAnnotation annotation = method.getAnnotation("io.qameta.allure.Step");
            if (annotation == null) return null;

            AutoStep step = new AutoStep();
            step.setAutoStepType(AutoStepType.Junit);

            // Извлекаем описание из аннотации
            PsiAnnotationMemberValue value = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
            String stepText = value != null ?
                    value.getText().replace("\"", "") :
                    method.getName();
            step.setStepText(stepText);

            // Ищем примеры использования
            step.setExamples(findStepExamples(method));
            step.setType(StepClassifier.classify(stepText, project));

            return step;
        });
    }

    private AutoStep createStepFromMethodCall(PsiMethodCallExpression call, ProgressIndicator indicator) {
        return ReadAction.compute(() -> {
            if (checkValid() || !call.isValid()) return null;

            AutoStep step = new AutoStep();
            step.setAutoStepType(AutoStepType.Junit);

            // Определяем тип вызова
            StepCallType callType = detectCallType(call);
            String stepText;
            PsiExpression[] args = call.getArgumentList().getExpressions();

            stepText = switch (callType) {
                case TEXT_ONLY, TEXT_WITH_BODY -> extractStepText(args[0]);
                case LAMBDA_ONLY -> generateNameForLambda(args[0]); // Генерация имени из лямбды
                case null -> "Unnamed step";
            };

            step.setStepText(stepText);
            indicator.setText2("Предобработка шага: " + step.getStepText());
            step.setExamples(findCallExamples(call, callType)); // Передаём тип вызова
            step.setType(StepClassifier.classify(stepText, project));

            return step;
        });
    }

    private String extractStepText(PsiExpression expression) {
        if (expression instanceof PsiLiteralExpression litExpr) {
            Object value = litExpr.getValue();
            if (value == null) {
                String text = litExpr.getText().replace("\"", "").trim();
                return text.isBlank()? "Unnamed step" : text;
            }
            return value.toString();
        }
        return expression.getText().replace("\"", ""); // Для переменных и сложных выражений
    }

    private String generateNameForLambda(PsiExpression lambda) {
        if (lambda instanceof PsiLambdaExpression) {
            PsiElement body = ((PsiLambdaExpression) lambda).getBody();
            // Анализ содержимого лямбды через LLM
            if (body != null) {
                try {
                    return StepClassifier.getStepText(body.getText(), project);
                } catch (Exception ignored) {
                }
            }
        }
        return "Custom Step"; // Стандартное имя
    }

    private StepCallType detectCallType(PsiMethodCallExpression call) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length == 0) return null;

        boolean hasText = args[0] instanceof PsiLiteralExpression
                || args[0] instanceof PsiBinaryExpression
                || args[0] instanceof PsiMethodCallExpression;
        boolean hasLambda = args[args.length - 1] instanceof PsiLambdaExpression;

        if (args.length == 1) {
            return hasText ? StepCallType.TEXT_ONLY : StepCallType.LAMBDA_ONLY;
        } else if (args.length == 2 && hasText && hasLambda) {
            return StepCallType.TEXT_WITH_BODY;
        }
        return null; // Некорректный вызов
    }

    private Set<String> findStepExamples(PsiMethod method) {
        if (checkValid() || !method.isValid()) return Collections.emptySet();

        return ReadAction.compute(() -> {
            if (checkValid() || !method.isValid()) return Collections.emptySet();

            Set<String> examples = new HashSet<>();
            ReferencesSearch.search(method).forEach(ref -> {
                if (examples.size() >= 5) return false;
                PsiElement element = ref.getElement();
                if (element instanceof PsiReferenceExpression) {
                    PsiElement parent = element.getParent();
                    if (parent instanceof PsiMethodCallExpression) {
                        examples.add(parent.getText());
                    }
                }
                return true;
            });

            if (examples.isEmpty()) {
                examples.add(getMethodSignature(method));
            }

            log.info("Найдено " + examples.size() + " примеров использования " + method.getName());
            return examples;
        });
    }

    private Set<String> findCallExamples(PsiMethodCallExpression call, StepCallType callType) {
        return ReadAction.compute(() -> {
            if (checkValid() || !call.isValid()) return Collections.emptySet();

            Set<String> examples = new HashSet<>();

            String callText = call.getText();// Сам вызов Allure.step
            if (callType == StepCallType.TEXT_ONLY) {
                examples.add(callText + "\n" + collectSubsequentCode(call));
            } else {
                examples.add(call.getText());
            }
            return examples;
        });
    }

    /**
     * Метод для извлечения из метода сигнатуры
     * @return String <тип> <название функции>(<аргументы>)
     */
    public String getMethodSignature(PsiMethod method) {
        StringBuilder signature = new StringBuilder();

        PsiType returnType = method.getReturnType();
        if (returnType != null) {
            signature.append(returnType.getPresentableText()).append(" ");
        }

        signature.append(method.getName());

        signature.append("(");
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            PsiParameter parameter = parameters[i];
            signature.append(parameter.getType().getPresentableText())
                    .append(" ")
                    .append(parameter.getName());

            if (i < parameters.length - 1) {
                signature.append(", ");
            }
        }
        signature.append(")");

        return signature.toString();
    }


    private String collectSubsequentCode(PsiMethodCallExpression stepCall) {
        StringBuilder exampleBuilder = new StringBuilder();
        PsiElement parent = stepCall.getParent();
        if (!(parent instanceof PsiExpressionStatement)) return "";

        PsiElement current = parent.getNextSibling();
        int stepsCounter = 0;
        final int maxSteps = 50; // Защита от бесконечных циклов

        while (current != null && stepsCounter++ < maxSteps) {
            if (current instanceof PsiWhiteSpace || current instanceof PsiComment) {
                current = current.getNextSibling();
                continue;
            }

            // Останавливаемся на следующем Allure.step
            if (isAllureStepCall(current)) break;

            // Останавливаемся на методах с аннотацией @Step
            if (isAnnotatedStepMethod(current)) break;

            // Останавливаемся на конце блока
            if (current instanceof PsiBlockStatement ||
                    current instanceof PsiReturnStatement ||
                    current instanceof PsiThrowStatement) {
                break;
            }

            // Добавляем релевантные элементы
            if (current instanceof PsiExpressionStatement) {
                exampleBuilder.append(current.getText()).append("\n");
            } else if (current instanceof PsiDeclarationStatement) {
                exampleBuilder.append(current.getText()).append("\n");
            }

            current = current.getNextSibling();
        }
        return exampleBuilder.toString();
    }

    private boolean isAnnotatedStepMethod(PsiElement element) {
        if (element instanceof PsiExpressionStatement) {
            PsiExpression expr = ((PsiExpressionStatement) element).getExpression();
            if (expr instanceof PsiMethodCallExpression) {
                PsiMethod method = ((PsiMethodCallExpression) expr).resolveMethod();
                if (method != null) {
                    PsiAnnotation stepAnnotation = method.getAnnotation("io.qameta.allure.Step");
                    return stepAnnotation != null;
                }
            }
        }
        return false;
    }

    private void pauseForCancellation(int millis, @Nullable ProgressIndicator indicator) {
        try {
            if (indicator != null) indicator.checkCanceled();
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }

    private enum StepCallType {
        TEXT_ONLY,      // Только текст (Allure.step("text"))
        LAMBDA_ONLY,    // Только лямбда (Allure.step(() -> {...}))
        TEXT_WITH_BODY  // Текст + лямбда (Allure.step("text", () -> {...}))
    }

}