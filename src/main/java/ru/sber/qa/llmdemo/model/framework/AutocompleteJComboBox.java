package ru.sber.qa.llmdemo.model.framework;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.TextAccessor;
import lombok.Getter;
import lombok.Setter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.*;


@Getter
@Setter
public class AutocompleteJComboBox extends JComboBox<String> implements TextAccessor {

    private Searchable<String, String> searchable;

    private JTextComponent jTextComponent;

    public AutocompleteJComboBox(Searchable<String, String> s) {
        super();
        this.searchable = s;
        setEditable(true);

        jTextComponent = (JTextComponent) getEditor().getEditorComponent();

        jTextComponent.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent arg0) {
            }

            @Override
            public void insertUpdate(DocumentEvent arg0) {
                update();
            }

            @Override
            public void removeUpdate(DocumentEvent arg0) {
                update();
            }


            public void update() {
                //perform separately, as listener conflicts between the editing component
                //and JComboBox will result in an IllegalStateException due to editing
                //the component when it is locked.

                SwingUtilities.invokeLater(() -> {

                    List<String> founds = new ArrayList<>(searchable.search(jTextComponent.getText()));

                    Set<String> foundSet = new HashSet<>();
                    for (String s1 : founds) {
                        foundSet.add(s1.toLowerCase());
                    }
                    Collections.sort(founds);//sort alphabetically

                    setEditable(false);
                    removeAllItems();

                    //if founds contains the search text, then only add once.
                    if (!foundSet.contains(jTextComponent.getText().toLowerCase())) {
                        addItem(jTextComponent.getText());
                    }

                    for (String s1 : founds) {
                        addItem(s1);
                    }

                    setEditable(true);
                    //setPopupVisible(true);
                    jTextComponent.requestFocus();
                });
            }
        });

        //When the text component changes, focus is gained
        //and the menu disappears. To account for this, whenever the focus
        //is gained by the JTextComponent and it has searchable values, we show the popup.

        jTextComponent.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent arg0) {
                if (!jTextComponent.getText().isEmpty()) {
                    setPopupVisible(true);
                    jTextComponent.setCaretPosition(jTextComponent.getText().length());
                }
            }

            @Override
            public void focusLost(FocusEvent arg0) {
            }
        });
    }

    @Override
    public void setText(String text) {
        jTextComponent.setText(text);
    }

    @Override
    public @NlsSafe String getText() {
        return jTextComponent.getText();
    }
}
