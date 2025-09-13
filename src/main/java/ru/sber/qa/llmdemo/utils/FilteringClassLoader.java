package ru.sber.qa.llmdemo.utils;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ExceptionUtil;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.function.Supplier;

/**
 * Лечит ошибку при работе с tool langchain4j и конфликте jackson из исходников idea
 * и используемого langchain4j версией jackson
 * java.util.ServiceConfigurationError: com.fasterxml.jackson.databind.Module: com.fasterxml.jackson.module.kotlin.KotlinModule not a subtype
 * Воспроизводится при вызове Tool langchain4j
 */
public class FilteringClassLoader extends ClassLoader {
    protected static final String ERROR_MESSAGE = "Ошибка при выполнении запроса к LLM ";
    protected static final com.intellij.openapi.diagnostic.Logger log = Logger.getInstance(FilteringClassLoader.class);

    public FilteringClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if ("META-INF/services/com.fasterxml.jackson.databind.Module".equals(name)) {
            // Фильтруем ресурс, содержащий KotlinModule
            return Collections.emptyEnumeration();
        }
        return super.getResources(name);
    }


    public static String runWithClassLoader(Supplier<String> runnable) {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            // Устанавливаем фильтрующий ClassLoader
            Thread.currentThread().setContextClassLoader(new FilteringClassLoader(originalClassLoader));
            return runnable.get();
        } catch (Exception | Error e) {
            log.error(e);
            return ERROR_MESSAGE + ExceptionUtil.getRootCause(e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader); // Восстанавливаем
        }
    }
}