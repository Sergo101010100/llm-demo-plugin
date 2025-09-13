package ru.sber.qa.llmdemo.utils;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class PsiUtils {

    public static String getAttributeValue(PsiAnnotationMemberValue value) {
        if (value == null) {
            return "";
        }
        return value.getText().replaceAll("\"", "");
    }

    public static String getAnnotationValue(PsiAnnotation annotation) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
        return value != null ? value.getText().replace("\"", "") : null;
    }


    public static List<String> extractKeyFromText(String annotationText) {
        List<String> result = new ArrayList<>();

        Pattern pattern = Pattern.compile("\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(annotationText);

        while (matcher.find()) {
            String value = matcher.group(1);
            if (!value.trim().isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }
}
