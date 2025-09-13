package ru.sber.qa.llmdemo;

import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.BaseRemoteFileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import org.jdesktop.swingx.VerticalLayout;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.sber.qa.llmdemo.dto.Test;
import ru.sber.qa.llmdemo.model.TestStepModel;

import javax.swing.*;
import java.awt.*;

/**
 * file editor для отображения ручных тестов
 */
public class TmsTestFileEditor extends BaseRemoteFileEditor {
    private final Test test;
    private final JComponent myPanel;
    private final VirtualFile virtualFile;

    public TmsTestFileEditor(@NotNull Project project, @NotNull Test test, @NotNull VirtualFile virtualFile) {
        super(project);
        this.test = test;
        this.virtualFile = virtualFile;
        this.myPanel = createEditorPanel();
    }

    private JComponent createEditorPanel() {
        JBTabbedPane tabbedPane = new JBTabbedPane();

        JPanel general = new JPanel();
        general.setLayout(new VerticalLayout());
        JBLabel key = new JBLabel(test.getKey());
        key.setBorder(BorderFactory.createTitledBorder("Ключ теста"));
        general.add(key);
        JBTextField testName = new JBTextField(test.getName());
        testName.setEnabled(false);
        testName.setBorder(BorderFactory.createTitledBorder("Имя теста"));
        general.add(testName);
        tabbedPane.addTab("Общая информация", general);

        JPanel steps = new JPanel();
        steps.setLayout(new VerticalLayout());
        JBTable tableSteps = getStep();
        steps.add(tableSteps.getTableHeader(), BorderLayout.NORTH);
        steps.add(tableSteps, BorderLayout.CENTER);
        tabbedPane.addTab("Шаги", steps);

        return tabbedPane;
    }

    private JBTable getStep() {
        JBTable table = new JBTable(new TestStepModel(test.getTestScript().getSteps()));
        table.setAutoResizeMode(JBTable.AUTO_RESIZE_ALL_COLUMNS);
        return table;
    }

    @Override
    public @NotNull JComponent getComponent() {
        return myPanel;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return myPanel;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getName() {
        return "TmsTest-File-Editor";
    }


    @Override
    protected @Nullable TextEditor getTextEditor() {
        return null;
    }

    @Override
    public VirtualFile getFile() {
        return this.virtualFile;
    }
}
