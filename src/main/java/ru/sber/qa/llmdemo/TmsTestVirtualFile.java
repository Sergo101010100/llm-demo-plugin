package ru.sber.qa.llmdemo;

import com.intellij.testFramework.LightVirtualFile;
import lombok.Getter;
import ru.sber.qa.llmdemo.dto.Test;

@Getter
public class TmsTestVirtualFile extends LightVirtualFile {
    private final Test test;

    public TmsTestVirtualFile(Test test) {
        super(test.getKey());
        this.test = test;
    }

}
