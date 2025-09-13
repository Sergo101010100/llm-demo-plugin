package ru.sber.qa.llmdemo.giga;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import ru.sber.qa.llmdemo.icon.TmsIcons;
import ru.sber.qa.llmdemo.index.TmsTest;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class TmsTestLLMToolWindow {
    private static final com.intellij.openapi.util.Key<TmsTest> KEY_TEST = Key.create("KEY_TEST");
    private static final Map<TmsTest, ChatPanel> openChats = new HashMap<>();

    private static final String ID = "TestSyncLLM";

    private static @NotNull ToolWindow getToolWindow(@NotNull Project project) {
        final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow lmmToolWindow = toolWindowManager.getToolWindow(ID);
        if (lmmToolWindow == null) {
            lmmToolWindow = toolWindowManager.registerToolWindow(ID, builder -> {
                builder.icon = TmsIcons.TMS_AUTOTEST;
                builder.anchor = ToolWindowAnchor.RIGHT;
                return Unit.INSTANCE;
            });
            lmmToolWindow.setIcon(TmsIcons.TMS_AUTOTEST);
        }
        return lmmToolWindow;
    }


    public static  boolean isToolWindowAvailable(Project project) {
        final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        return toolWindowManager.getToolWindow(ID) != null;
    }


    public static void showChat(Project project, TestGenerator testGenerator) {

        ToolWindow toolWindow = getToolWindow(project);
        if (!toolWindow.isAvailable()) {
            toolWindow.setAvailable(true);
        }

        if (openChats.containsKey(testGenerator.getTmsTest())) {
            selectTab(project, testGenerator.getTmsTest());
            return;
        }
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(10));


        // Создаем компонент с текстом
        EditorNotificationPanel infoPanel = new EditorNotificationPanel();
        infoPanel.setText(
                """
                        <html>
                        <b style="color:red">Информация:</b>
                        <p>В данном окне с помощью AI-ассистента вы можете отредактировать только выбранный вами автотест.</p>
                        <p>Так же AI-ассистент может:</p>
                        <ul>
                            <li>Искать симантически похожие шаги и тесты и использовать их</li>
                            <li>Читать ваши методы и классы и использовать их в автотестах</li>
                        </ul>
                        <p style="color:red">После завершения работы с автотестом обязательно закройте чат</p>
                        </html>
                        """
        );

        mainPanel.add(new JBScrollPane(infoPanel), BorderLayout.NORTH);

        // Добавляем панель чата
        ChatPanel chatPanel = new ChatPanel(project, testGenerator);
        mainPanel.add(chatPanel, BorderLayout.CENTER);

        // Создаем контент для вкладки
        ContentFactory factory = ContentFactory.getInstance();
        TmsTest key = testGenerator.getTmsTest();
        Content content = factory.createContent(mainPanel, key.getKey(), false);
        content.setDisposer(() -> {
            openChats.remove(key);
            if (toolWindow.getContentManager().getContentCount() == 0) {
                toolWindow.setAvailable(false);
            }
        });
        content.putUserData(KEY_TEST, key);
        content.setCloseable(true);

        // Добавляем вкладку
        toolWindow.getContentManager().addContent(content);
        toolWindow.setAvailable(true);
        toolWindow.activate(null);

        // Выбираем новую вкладку
        toolWindow.getContentManager().setSelectedContent(content);

        openChats.put(testGenerator.getTmsTest(), chatPanel);
    }


    private static void selectTab(Project project, TmsTest key) {
        ToolWindow myToolWindow = getToolWindow(project);
        for (Content content : myToolWindow.getContentManager().getContents()) {
            if (key.equals(content.getUserData(KEY_TEST))) {
                myToolWindow.getContentManager().setSelectedContent(content);
                myToolWindow.activate(null);
                return;
            }
        }
    }


    public static void closeTab(Project project, TmsTest key) {
        if(!isToolWindowAvailable(project)) {
            return;
        }
        ToolWindow myToolWindow = getToolWindow(project);
        for (Content content : myToolWindow.getContentManager().getContents()) {
            if (key.equals(content.getUserData(KEY_TEST))) {
                myToolWindow.getContentManager().removeContent(content, true);
                break;
            }
        }
    }
}