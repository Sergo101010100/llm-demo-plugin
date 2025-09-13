package ru.sber.qa.llmdemo.settings;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;

import javax.swing.*;
import java.awt.*;

public class AiSettingsComponent {
    private final JPanel myMainPanel;

    private final JBTextArea area = new JBTextArea();

    public AiSettingsComponent(Project project) {
        JBScrollPane areaPane = new JBScrollPane(area);
        areaPane.setPreferredSize(new Dimension(300, 300));
        areaPane.setBorder(BorderFactory.createTitledBorder("Дополнительные инструкции"));


        JButton createEmbeddingDataBase = new JButton("Заиндексировать проект");

        createEmbeddingDataBase.addActionListener(new IndexingAction(project));

        JBLabel verifyLabel = new JBLabel();
        myMainPanel = FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("""
                        <html>
                        <b style="color:red">Информация:</b>
                        <p>В данном окне вы можете указать дополнительные инструкции для AI-ассистента</p>
                        <p>На текущий момент инструкции только к самому автотесту</p>
                        <p>Так же у ассисента есть возможно использовать инструменты:</p>
                        <ul>
                        <li> Чтение методов и классов из проетка </li>
                        <li> Симантический поиск шагов и тестов </li>
                        </ul>
                        <b>Что тут можно описать: </b>
                         <ul>
                        <li>Типовую структуру ваших тестов </li>
                        <li>Указать примеры тестов, которые можно использовать </li>
                        <li>Указать какие методы\\классы\\библиотеки нужно использовать </li>
                        <li>Указать в явном виде использование инструментов и описать в каких случаях их использовать </li>
                        </ul>
                        </html>
                        """))
                .addComponent(areaPane)

                .addSeparator()
                .addComponent(verifyLabel)
                .addComponent(createEmbeddingDataBase)
                .addSeparator()
                .getPanel();
    }


    public JPanel getPanel() {
        return myMainPanel;
    }


    public String getAdditionalInstructions() {
        if (area.getText().isEmpty()) {
            return "";
        } else {
            return area.getText();
        }
    }

    public void setAdditionalInstructions(String additionalInstructions) {
        area.setText(additionalInstructions);
    }
}