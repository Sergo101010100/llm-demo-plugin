package ru.sber.qa.llmdemo.utils;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class MessagesUtils extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.TmsBundle";

    private MessagesUtils() {
        super(BUNDLE);
    }

    public static final MessagesUtils INSTANCE = new MessagesUtils();

    public static String get(@PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }
}
