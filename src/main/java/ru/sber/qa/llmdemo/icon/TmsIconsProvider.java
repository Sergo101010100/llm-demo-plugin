package ru.sber.qa.llmdemo.icon;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.sber.qa.llmdemo.TmsTestVirtualFile;

import javax.swing.*;

public class TmsIconsProvider implements FileIconProvider {
    @Override
    public @Nullable Icon getIcon(@NotNull VirtualFile file, int flags, @Nullable Project project) {
        if (file instanceof TmsTestVirtualFile) {
            return TmsIcons.TMS_ICON;
        }
        return null;
    }
}
