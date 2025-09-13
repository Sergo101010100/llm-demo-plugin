package ru.sber.qa.llmdemo.utils;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

@UtilityClass
public final class SlowUtils {

    /**
     * Синхронный запуск с прогресс баром
     *
     * @param project        проект
     * @param title          заголовок прогресса
     * @param computable     операция для выполнения
     * @param <T>            тип результата
     */
    public static <T> T runSyncWithProgress(@NotNull Project project,
                                            @NotNull String title,
                                            @NotNull Supplier<T> computable) {
        return ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> ReadAction.compute(computable::get),
                title,
                true,
                project
        );
    }

    /**
     * Синхронный запуск с прогресс баром
     *
     * @param project        проект
     * @param title          заголовок прогресса
     * @param runnable     операция для выполнения
     * @param <T>            тип результата
     */
    public static <T> void runSyncWithProgress(@NotNull Project project,
                                               @NotNull String title,
                                               @NotNull Runnable runnable) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
                runnable::run,
                title,
                true,
                project
        );
    }
}
