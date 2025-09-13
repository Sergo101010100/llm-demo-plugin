package ru.sber.qa.llmdemo.icon;

import com.intellij.openapi.util.IconLoader;
import lombok.experimental.UtilityClass;

import javax.swing.*;

@UtilityClass
/**
 * utility class for icons plugin
 */
public class TmsIcons {

    public static final Icon TMS_ICON = IconLoader.getIcon("icons/tms.svg", TmsIcons.class);

    public static final Icon TMS_AUTOTEST = IconLoader.getIcon("icons/autotest.svg", TmsIcons.class);
}
