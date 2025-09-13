package ru.sber.qa.llmdemo.model;


import ru.sber.qa.llmdemo.dto.Step;

import javax.swing.table.DefaultTableModel;
import java.util.List;

/**
 * Модель таблицы шагов теста
 */
public class TestStepModel extends DefaultTableModel {
    private final List<Step> steps;

    public TestStepModel(List<Step> steps) {
        super(prepareData(steps),
                new Object[]{"Description", "Expected Result", "Test Data"});
        this.steps = steps;
    }


    private static Object[][] prepareData(List<Step> steps) {
        Object[][] data = new Object[steps.size()][3];
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            data[i][0] = step.getDescription();
            data[i][1] = step.getTestData();
            data[i][2] = step.getExpectedResult();
        }
        return data;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return switch (columnIndex) {
            case 0 -> steps.get(rowIndex).getDescription();
            case 1 -> steps.get(rowIndex).getTestData();
            case 2 -> steps.get(rowIndex).getExpectedResult();
            default -> throw new RuntimeException();
        };
    }

}
