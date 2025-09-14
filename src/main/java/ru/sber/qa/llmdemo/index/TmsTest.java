package ru.sber.qa.llmdemo.index;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import lombok.Getter;
import lombok.Setter;
import ru.sber.qa.llmdemo.dto.Test;

import java.util.Objects;

public class TmsTest {

    @Getter
    @Setter
    private String filePath;
    @Getter
    @Setter
    private int offset;
    @Getter
    @Setter
    protected String key;

    @Getter
    @Setter
    private TestLanguage testLanguage;

    @Getter
    @Setter
    private Test test;

    private transient PsiElement testProject;

    public TmsTest(String key) {
        this.key = key;
    }

    public TmsTest(String key, TestLanguage testLanguage, String filePath, int offset) {
        this.key = key;
        this.testLanguage = testLanguage;
        this.filePath = filePath;
        this.offset = offset;
    }

    public TmsTest(String key, PsiElement testProject) {
        this.key = key;
        setTestProject(testProject);
    }

    public void setTestProject(PsiElement element) {
        this.testProject = element;
        PsiFile file = element.getContainingFile();
        VirtualFile virtualFile = file.getVirtualFile();
        this.filePath = virtualFile != null ? virtualFile.getPath() : null;
        this.offset = element.getTextOffset();
    }


    // Ленивое восстановление элемента
    public PsiElement getTestProject(Project project) {
        if (testProject == null && filePath != null) {
            testProject = restorePsiElement(project, filePath, offset);
        }
        return testProject;
    }

    private PsiElement restorePsiElement(Project project, String filePath, int offset) {
        return DumbService.getInstance(project).runReadActionInSmartMode(() -> {
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath);
            if (virtualFile == null || !virtualFile.isValid()) return null;

            PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
            if (file == null || !file.isValid()) return null;

            PsiElement element = file.findElementAt(offset);
            if (element == null) return null;

            return element.getParent();
        });
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TmsTest tmsTest = (TmsTest) o;
        return Objects.equals(key, tmsTest.key) &&
                Objects.equals(filePath, tmsTest.getFilePath())
                && Objects.equals(offset, tmsTest.getOffset());
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, filePath, offset);
    }


    @Override
    public String toString() {
        return test.getName();
    }
}
