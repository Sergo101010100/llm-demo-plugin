package ru.sber.qa.llmdemo.model.framework;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.swing.*;
import java.util.List;
import java.util.stream.Stream;

import static ru.sber.qa.llmdemo.model.framework.FrameworkName.Junit5;


public class ModulesComboBox extends ComboBox<String> {

    public ModulesComboBox(Project project, FrameworkName frameworkName) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        if (frameworkName == Junit5) {
            model.addAll(Stream.of(ModuleManager.getInstance(project).getModules()).filter(ModulesComboBox::isModuleWithTests).map(Module::getName).toList());
        } else {
            model.addAll(Stream.of(ModuleManager.getInstance(project).getModules()).map(Module::getName).toList());
        }
        setModel(model);
    }

    public ModulesComboBox(List<Module> modules) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addAll(modules.stream().map(Module::getName).toList());
        setModel(model);
    }

    public static boolean isModuleWithTests(Module module) {
        if (module == null) {
            return false;
        }
        return !ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.TEST_SOURCE).isEmpty();
    }
}
