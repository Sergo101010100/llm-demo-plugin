package ru.sber.qa.llmdemo.giga;

import com.intellij.icons.AllIcons;
import com.intellij.markdown.utils.MarkdownToHtmlConverter;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor;
import org.jdesktop.swingx.VerticalLayout;
import org.jetbrains.annotations.NotNull;
import ru.sber.qa.llmdemo.index.TmsTest;
import ru.sber.qa.llmdemo.utils.MdUtils;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import static com.intellij.microservices.mime.MimeTypes.TEXT_HTML;


public class ChatPanel extends JPanel {
    protected static final Logger log = Logger.getInstance(ChatPanel.class);
    private final GigaService gigaService;
    private final Project project;
    private final JTextArea chatInput = new JTextArea(3, 50);
    private final JButton sendButton = new JButton("Отправить в LLM");
    private final JButton exportButton = new JButton("Экспорт диалога");
    private final JButton closeButton = new JButton(AllIcons.Actions.Close);
    private final JPanel historyBox = new JPanel(new VerticalLayout()); // Основной контейнер истории
    private final JPanel historyContainer = new JPanel(new BorderLayout()); // Контейнер для скролла

    private final java.util.List<String> chatHistory = new ArrayList<>();
    private final TmsTest tmsTest;
    private final TestGenerator testGenerator;


    public ChatPanel(Project project,
                     TestGenerator testGenerator) {
        super(new BorderLayout(5, 5));
        this.testGenerator = testGenerator;
        this.tmsTest = testGenerator.getTmsTest();
        this.gigaService = testGenerator.getFramework().getGigaService();
        this.project = project;
        initComponents();
    }

    private void initComponents() {
        Color borderColor = JBColor.border();
        Border border = JBUI.Borders.compound(
                JBUI.Borders.customLine(borderColor, 3),
                JBUI.Borders.empty(5)
        );

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(JBUI.Borders.empty(5));
        JLabel titleLabel = new JLabel("Чат для изменения теста: " + tmsTest.getKey());

        headerPanel.add(titleLabel, BorderLayout.WEST);
        closeButton.setToolTipText("Закрыть чат");
        closeButton.addActionListener(e -> closeChat());
        closeButton.setBorder(JBUI.Borders.customLine(borderColor, 1));
        headerPanel.add(closeButton, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Настройка контейнера истории
        historyContainer.add(new JLabel("История:"), BorderLayout.NORTH);
        JBScrollPane scrollPane = new JBScrollPane(historyBox);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        historyBox.setBorder(border);
        historyContainer.add(scrollPane, BorderLayout.CENTER);

        // Настройка области ввода
        chatInput.setLineWrap(true);
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.add(new JLabel("Ваш вопрос:"), BorderLayout.NORTH);
        JScrollPane inputScrollPane = new JScrollPane(chatInput);
        inputScrollPane.setBorder(border);
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);
//        Обработка нажатия enter для отправки
        chatInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (!e.isShiftDown()) {
                        e.consume();
                        sendRequest(new ActionEvent(chatInput, ActionEvent.ACTION_PERFORMED, ""));
                    }
                }
            }
        });


        AnAction exportAction = new AnAction("Экспорт Диалога", "Экспорт истории чата в файл", AllIcons.Actions.MenuPaste) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                exportChatHistory();
            }
        };

        // Создаем тулбар
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(
                "ChatActions",
                new DefaultActionGroup(exportAction),
                false
        );

        // Панель кнопки
        JPanel buttonPanel = new JPanel(new BorderLayout());
        sendButton.addActionListener(this::sendRequest);
        JPanel sendButtonPanel = new JPanel();
        sendButtonPanel.add(sendButton);
        buttonPanel.add(sendButtonPanel, BorderLayout.CENTER);
        toolbar.setTargetComponent(buttonPanel);
        buttonPanel.add(toolbar.getComponent(), BorderLayout.EAST);

        inputPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Компоновка главного окна
        add(historyContainer, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);
        setBorder(JBUI.Borders.empty(10));

    }

    private void sendRequest(ActionEvent e) {
        String userRequest = chatInput.getText().trim();
        if (userRequest.isEmpty()) return;

        // Добавляем запрос пользователя
        appendUserMessage(userRequest);
        chatInput.setText("");

        // Добавляем индикатор загрузки
        AsyncProcessIcon loader = new AsyncProcessIcon("Thinking");
        JPanel loaderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        loaderPanel.add(new JLabel("Ассистент: "));
        loaderPanel.add(loader);
        loaderPanel.setVisible(false);
        addMessageToHistory(loaderPanel);

        // Запускаем асинхронную задачу
        Task.Backgroundable task = new Task.Backgroundable(project, "Обработка запроса", true) {
            String responseLlm;
            Exception error;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                // Блокируем UI элементы
                loaderPanel.setVisible(true);
                sendButton.setEnabled(false);
                exportButton.setEnabled(false);

                try {
                    String currentCode = ReadAction.compute(() -> tmsTest.getTestProject(project).getText());
                    responseLlm = gigaService.getAgent().refineGeneratedTest(tmsTest.getKey(), currentCode, userRequest);
                    log.info("refine llm answer:\n" + responseLlm);
                } catch (Exception ex) {
                    error = ex;
                }
            }

            @Override
            public void onFinished() {
                // Обрабатываем результат
                if (error != null) {
                    appendCollapsedAssistantMessage("Ошибка", error.getMessage());
                } else {
                    try {
                        String refinedCode = MdUtils.getCodeBlock(responseLlm);
                        if (!refinedCode.isEmpty()) {
                            testGenerator.getFramework().updateTest(refinedCode);
                            appendCollapsedAssistantMessage("Код обновлен", responseLlm);
                        } else {
                            appendCollapsedAssistantMessage("Ответ без изменений кода", responseLlm);
                        }
                    } catch (Exception ex) {
                        appendCollapsedAssistantMessage("Ошибка обработки", ex.getMessage());
                    }
                }
                // Разблокируем UI
                sendButton.setEnabled(true);
                exportButton.setEnabled(true);
                loaderPanel.setVisible(false);
                revalidateHistory();
            }
        };

        ProgressManager.getInstance().run(task);
    }

    private void appendUserMessage(String message) {
        String formatted = "### Пользователь:\n" + message;
        chatHistory.add(formatted);
        JEditorPane messageArea = createMessageArea("Вы: " + message);
        addMessageToHistory(messageArea);
    }

    public void appendCollapsedAssistantMessage(String summary, String fullMessage) {
        String formatted = "### Ассистент:\n" + fullMessage;
        chatHistory.add(formatted);
        // Панель для сообщения
        JPanel messagePanel = new JPanel();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
        messagePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        messagePanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

        // Заголовок (кнопка для разворачивания)
        JButton toggleButton = new JButton("Ассистент: " + summary + " ▼");
        styleToggleButton(toggleButton);

        // Область с полным текстом (изначально скрыта)
        JEditorPane fullTextArea = createMessageArea(fullMessage);
        fullTextArea.setVisible(false);

        // Обработчик клика
        toggleButton.addActionListener(e -> {
            boolean visible = !fullTextArea.isVisible();
            fullTextArea.setVisible(visible);
            toggleButton.setText("Ассистент: " + summary + (visible ? " ▲" : " ▼"));
            revalidateHistory();
        });

        messagePanel.add(toggleButton);
        messagePanel.add(fullTextArea);
        historyBox.add(messagePanel);
        revalidateHistory();
    }

    private JEditorPane createMessageArea(String text) {
        var flavour = new CommonMarkFlavourDescriptor();
        String html = new MarkdownToHtmlConverter(flavour).convertMarkdownToHtml(text, null);

        final JEditorPane area = new JEditorPane();
        area.setContentType(TEXT_HTML);
        area.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        area.setBackground(new JBColor(Gray._240, JBColor.background()));

        area.setText(html);
        return area;
    }

    private void styleToggleButton(JButton button) {
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setForeground(JBColor.BLUE);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
    }

    private void addMessageToHistory(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        historyBox.add(component);
        historyBox.add(Box.createVerticalStrut(5));
        revalidateHistory();
    }

    private void revalidateHistory() {
        historyBox.revalidate();
        historyBox.repaint();

        // Авто-скролл к низу
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = ((JBScrollPane) historyContainer.getComponent(1)).getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void exportChatHistory() {
        FileSaverDescriptor descriptor = new FileSaverDescriptor("Экспорт Диалога", "Сохраните историю чата", "md");
        VirtualFileWrapper fileWrapper = FileChooserFactory.getInstance()
                .createSaveFileDialog(descriptor, project)
                .save("chat_history.md");

        if (fileWrapper != null) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileWrapper.getFile()))) {
                for (String entry : chatHistory) {
                    writer.write(entry);
                    writer.newLine();
                    writer.newLine(); // Разделение сообщений
                }
            } catch (IOException ex) {
                Messages.showErrorDialog("Ошибка экспорта: " + ex.getMessage(), "Ошибка");
            }
        }
    }

    private void closeChat() {
        gigaService.clearMemory(tmsTest);
    }

}