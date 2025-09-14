package ru.sber.qa.llmdemo;

import com.intellij.testFramework.LightVirtualFile;
import lombok.Getter;
import ru.sber.qa.llmdemo.index.TmsTest;

@Getter
public class TmsTestVirtualFile extends LightVirtualFile {
    private final TmsTest test;

    public TmsTestVirtualFile(TmsTest test) {
        super(test.getKey());
        this.test = test;
    }

}
