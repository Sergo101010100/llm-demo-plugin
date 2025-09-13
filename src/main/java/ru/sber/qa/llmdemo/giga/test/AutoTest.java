package ru.sber.qa.llmdemo.giga.test;

import lombok.Data;
import ru.sber.qa.llmdemo.index.TestLanguage;

@Data
public class AutoTest {
    private String key;
    private String llmDescription;
    private String body;
    private AutoTestType type;
    private TestLanguage language;
    private String keywords;
    private boolean background;

}
