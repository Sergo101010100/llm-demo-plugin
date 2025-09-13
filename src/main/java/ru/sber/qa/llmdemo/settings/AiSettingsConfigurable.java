package ru.sber.qa.llmdemo.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AiSettingsConfigurable implements Configurable {

    private AiSettingsComponent aiSettingsComponent;
    private final Project myProject;

    public AiSettingsConfigurable(Project project) {
        this.myProject = project;
    }

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return "My Plugin LLM Settings";
    }

    @Override
    public @Nullable JComponent createComponent() {
        aiSettingsComponent = new AiSettingsComponent(myProject);
        return aiSettingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        AppSettingsState settings = AppSettingsState.getInstance(myProject);
        return !settings.getAdditionalInstructions().equals(aiSettingsComponent.getAdditionalInstructions());
    }

    @Override
    public void apply() {
        var settings = AppSettingsState.getInstance(myProject);
        settings.setAdditionalInstructions(aiSettingsComponent.getAdditionalInstructions());
    }

    @Override
    public void reset() {
        AppSettingsState settings = AppSettingsState.getInstance(myProject);
        aiSettingsComponent.setAdditionalInstructions(settings.getAdditionalInstructions());
    }
}