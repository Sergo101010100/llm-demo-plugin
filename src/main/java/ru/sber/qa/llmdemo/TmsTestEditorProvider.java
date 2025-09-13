package ru.sber.qa.llmdemo;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import ru.sber.qa.llmdemo.dto.Test;

public class TmsTestEditorProvider implements FileEditorProvider, DumbAware {

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile virtualFile) {
        return virtualFile instanceof TmsTestVirtualFile;
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile virtualFile) {
        Test test = ((TmsTestVirtualFile) virtualFile).getTest();
        return new TmsTestFileEditor(project,test,virtualFile);
    }

    @Override
    public @NotNull @NonNls String getEditorTypeId() {
        return "Zephyr-Test-Editor";
    }

    @Override
    public @NotNull FileEditorPolicy getPolicy() {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }


}
