package ru.sber.qa.llmdemo.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class TmsMessage {
    private static final com.intellij.openapi.diagnostic.Logger log = Logger.getInstance(TmsMessage.class);

    public static void message(String msg, NotificationType type) {
        Notifications.Bus.notify(new Notification("TmsMessage",
                msg,
                type));
        if (type == NotificationType.ERROR) {
            log.error("Error '%s' in TestSync plugin".formatted(msg));
        }
    }
}
