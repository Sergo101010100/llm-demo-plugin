package ru.sber.qa.llmdemo.giga.test;

public enum AutoTestType {
    UI, DB, REST, KAFKA, OTHER;


    public static AutoTestType getAutoTestType(String type) {
        try {
            return AutoTestType.valueOf(type);
        } catch (Exception e) {
            return AutoTestType.OTHER;
        }
    }
}
