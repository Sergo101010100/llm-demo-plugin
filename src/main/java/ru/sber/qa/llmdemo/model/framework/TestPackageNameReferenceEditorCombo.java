package ru.sber.qa.llmdemo.model.framework;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TestPackageNameReferenceEditorCombo extends ReferenceEditorComboWithBrowseButton {
    private final List<Consumer<String>> packageChangeListeners = new ArrayList<>();

    public TestPackageNameReferenceEditorCombo(String text, @NotNull Module module,
                                               @NlsContexts.DialogTitle String chooserTitle) {
        super(null, text, module.getProject(), false, "");

        getChildComponent().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                notifyPackageChanged(getText());
            }
        });

        addActionListener(e -> {
            TestPackageChooserDialog chooser = new TestPackageChooserDialog(chooserTitle, module.getProject(), module);
            chooser.selectPackage(getText());
            if (chooser.showAndGet()) {
                final PsiPackage aPackage = chooser.getSelectedPackage();
                if (aPackage != null) {
                    setText(aPackage.getQualifiedName());
                }
            }
        });
    }

    public void addPackageChangeListener(Consumer<String> listener) {
        packageChangeListeners.add(listener);
    }

    private void notifyPackageChanged(String newPackageName) {
        SwingUtilities.invokeLater(() -> {
            for (Consumer<String> listener : packageChangeListeners) {
                listener.accept(newPackageName);
            }
        });
    }
}
