package ru.sber.qa.llmdemo.model.framework;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UastFacade;
import ru.sber.qa.llmdemo.index.TestLanguage;
import ru.sber.qa.llmdemo.utils.MessagesUtils;
import ru.sber.qa.llmdemo.utils.SlowUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ClassNameAutocomplete extends AutocompleteJComboBox {
    private final Project project;
    private final TestLanguage testLanguage;

    public ClassNameAutocomplete(Module myModule, String packageOrFileName, @NotNull TestLanguage testLanguage) {
        super(createSearchable(myModule, packageOrFileName, testLanguage));
        this.project = myModule.getProject();
        this.testLanguage = testLanguage;
    }

    public void setPackageName(Module newModule, String packageName, @NotNull TestLanguage testLanguage) {
        // Пересоздаем Searchable с новым пакетом
        setSearchable(createSearchable(newModule, packageName, testLanguage));
    }


    private static Searchable<String, String> createSearchable(Module myModule, String myPackageName, TestLanguage testLanguage) {
        return new Searchable<>() {
            final Set<String> classNameList = new HashSet<>();

            private void getClassNames() {
                Project project = myModule.getProject();
                FileIndex fileIndex = ModuleRootManager.getInstance(myModule).getFileIndex();
                PsiManager psiManager = PsiManager.getInstance(project);

                SlowUtils.runSyncWithProgress(project, MessagesUtils.get("search.java.class"),
                        () -> fileIndex.iterateContent(fileOrDir -> {
                            if (fileOrDir.isDirectory()) {
                                if (testLanguage == TestLanguage.JAVA && fileIndex.isUnderSourceRootOfType(fileOrDir, ContainerUtil.newHashSet(JavaSourceRootType.TEST_SOURCE))) {
                                    searchJavaClass(psiManager, fileOrDir);
                                }
                            }
                            return true;
                        }));
            }

            @Override
            public Collection<String> search(String value) {
                getClassNames();
                Set<String> founds = new HashSet<>();
                for (String s : classNameList) {
                    if (s.contains(value)) {
                        founds.add(s);
                    }
                }
                return founds;
            }

            private void searchJavaClass(PsiManager psiManager, VirtualFile fileOrDir) {
                PsiDirectory psiDirectory = psiManager.findDirectory(fileOrDir);

                if (psiDirectory != null) {
                    PsiDirectory testTargetPackage = psiDirectory;
                    for (String packageNamePart : myPackageName.split("\\.")) {
                        testTargetPackage = testTargetPackage.findSubdirectory(packageNamePart);
                        if (testTargetPackage == null) break;
                    }

                    if (testTargetPackage != null) {
                        for (PsiFile psiFile : testTargetPackage.getFiles()) {
                            UFile uFile = (UFile) UastFacade.INSTANCE.convertElementWithParent(psiFile, UFile.class);
                            if (uFile != null) {
                                Collection<PsiClass> classes = PsiTreeUtil.findChildrenOfType(uFile.getJavaPsi(), PsiClass.class);

                                for (PsiClass psiClass : classes) {
                                    if (psiClass.getName() != null) {
                                        classNameList.add(psiClass.getName());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        };
    }

    // Проверка валидности поля
    public boolean isFieldValid() {
        return switch (testLanguage) {
            case JAVA -> PsiNameHelper.getInstance(project).isIdentifier(super.getText());
            //Как будто для остальных языков ограничений нет. Если появлятся - добавить тут
            case null, default -> true;
        };
    }

}
