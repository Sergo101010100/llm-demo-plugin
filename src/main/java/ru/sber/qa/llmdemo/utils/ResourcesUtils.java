package ru.sber.qa.llmdemo.utils;

import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@UtilityClass
public class ResourcesUtils {

    public static String getResources(String path) {
        try (InputStream inputStream = ResourcesUtils.class.getClassLoader().getResourceAsStream(path)) {
            return new String(inputStream.readAllBytes());
        } catch (IOException e) {
            return "";
        }
    }

    public static Properties getPluginProperties() {
        try {
            Properties properties = new Properties();
            properties.load(ResourcesUtils.class.getClassLoader().getResourceAsStream("plugin.properties"));
            return properties;
        } catch (IOException e) {
            return new Properties();
        }
    }
}
