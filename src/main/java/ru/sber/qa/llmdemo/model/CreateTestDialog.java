package ru.sber.qa.llmdemo.model;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleListCellRenderer;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import ru.sber.qa.llmdemo.giga.TestGenerator;
import ru.sber.qa.llmdemo.index.TmsTest;
import ru.sber.qa.llmdemo.model.framework.AbstractTestFramework;
import ru.sber.qa.llmdemo.model.framework.TestJunit5Framework;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.LinkedList;


public class CreateTestDialog extends DialogWrapper {

    private ComboBox<AbstractTestFramework> myLibrariesCombo;
    @Getter
    private AbstractTestFramework selectedFramework;

    private JPanel mainPanel;
    PsiElement generatedTest = null;
    private final TmsTest testCase;
    @Getter
    private boolean aiGenerationSelected = false;


    public CreateTestDialog(@Nullable Project project, TmsTest testCase) {
        super(project, false);
        this.testCase = testCase;
        setTitle("Create Test");
        if (project != null && !DumbService.getInstance(project).isDumb()) {
            java.util.List<AbstractTestFramework> frameworks = new LinkedList<>();
            frameworks.add(new TestJunit5Framework(project, testCase));


            DefaultComboBoxModel<AbstractTestFramework> model = new DefaultComboBoxModel<>();
            frameworks.forEach(model::addElement);
            this.myLibrariesCombo = new ComboBox<>(model);

            myLibrariesCombo.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
                if (value != null) {
                    label.setText(value.getFrameworkName().name());
                    label.setIcon(value.getIcon());
                }
            }));


            selectedFramework = (AbstractTestFramework) myLibrariesCombo.getSelectedItem();
            init();
        }
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEtchedBorder());
        mainPanel.setPreferredSize(new Dimension(500, 50));

        mainPanel.add(myLibrariesCombo, BorderLayout.NORTH);
        JPanel frameworkPanel = (JPanel) selectedFramework.getPanel(this);
        initCheckClassName(selectedFramework);
        mainPanel.add(frameworkPanel, BorderLayout.CENTER);

        myLibrariesCombo.addItemListener(l -> {
            selectedFramework = (AbstractTestFramework) myLibrariesCombo.getSelectedItem();
            mainPanel.remove(mainPanel.getComponent(1));
            JPanel newPanel = (JPanel) selectedFramework.getPanel(this);
            initCheckClassName(selectedFramework);
            mainPanel.add(newPanel, BorderLayout.CENTER);
            mainPanel.repaint();
            pack();
        });

        return mainPanel;
    }


    @Override
    protected void doOKAction() {
        TestGenerator testGenerator = new TestGenerator(selectedFramework, testCase);
        if (selectedFramework.isAiTestGenerated()) {
            aiGenerationSelected = true;
        } else {
            generatedTest = testGenerator.generateTestElement();
            if (generatedTest == null) {
                Messages.showErrorDialog("Не удалось создать тест", "Error");
                return;
            }
        }
        super.doOKAction();
    }

    public @Nullable PsiElement getGeneratedTest() {
        return generatedTest;
    }



    /**
     * Валидируем имя класса\файла
     *
     * @param testFramework
     */
    private void initCheckClassName(AbstractTestFramework testFramework) {
        if (testFramework != null && testFramework.getMyTargetClassNameField() != null) {
            testFramework.getMyTargetClassNameField().getJTextComponent().getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    checkValid();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    checkValid();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    checkValid();
                }

                private void checkValid() {
                    boolean isFieldValid = selectedFramework.getMyTargetClassNameField().isFieldValid();

                    Color fgColor = isFieldValid ? JBColor.foreground()
                            : new JBColor(JBColor.RED, JBColor.RED);

                    selectedFramework.getMyTargetClassNameField().getJTextComponent().setForeground(fgColor);

                    getOKAction().setEnabled(isFieldValid);
                }
            });

        }
    }

}
