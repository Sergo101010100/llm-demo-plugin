package ru.sber.qa.llmdemo.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
@State(
        name = "ru.sber.qa.llmdemo.settings.AppSettingsState"
)
@Getter
@Setter
public final class AppSettingsState implements PersistentStateComponent<AppSettingsState> {


    private String additionalInstructions = "";

    public static AppSettingsState getInstance(Project project) {
        return project.getService(AppSettingsState.class);
    }

    @Override
    public @NotNull AppSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull AppSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }


}