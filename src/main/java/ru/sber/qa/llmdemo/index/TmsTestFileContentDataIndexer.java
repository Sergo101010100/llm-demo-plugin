package ru.sber.qa.llmdemo.index;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;
import ru.sber.qa.llmdemo.utils.UastUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TmsTestFileContentDataIndexer implements DataIndexer<String, TmsTest, FileContent> {

    @Override
    public @NotNull Map<String, TmsTest> map(@NotNull FileContent inputData) {
        Map<String, TmsTest> result;
        result = new HashMap<>();
        PsiFile file = PsiManager.getInstance(inputData.getProject()).findFile(inputData.getFile());

        if (file != null && !isSystemFile(file)) {
            if (file instanceof PsiJavaFile) {
                processJvmTests(file, result);
            }
        }
        return result;
    }


    private void processJvmTests(PsiFile file, Map<String, TmsTest> result) {
        file.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                super.visitMethod(method);
                try {
                    if (UastUtils.isTest(method)) {
                        List<String> keys = UastUtils.getKeysTest(method);
                        for (String key : keys) {
                            TmsTest tmsTest = new TmsTest(key, method);
                            tmsTest.setTestLanguage(TestLanguage.JAVA);
                            result.put(key, tmsTest);
                        }
                    }
                } catch (Exception ignore) {
                }
            }
        });
    }

    private boolean isSystemFile(PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        return virtualFile == null ||
                virtualFile.getFileSystem().getProtocol().equals("jar") ||
                virtualFile.getPath().contains("src.zip");
    }
}
