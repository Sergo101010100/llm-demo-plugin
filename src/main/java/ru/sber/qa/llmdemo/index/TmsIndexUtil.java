package ru.sber.qa.llmdemo.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import lombok.experimental.UtilityClass;
import ru.sber.qa.llmdemo.utils.SlowUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static ru.sber.qa.llmdemo.index.TmsIndexExtension.TMS_INDEX_KEY;


@UtilityClass
public class TmsIndexUtil {

    public static TmsTest findTestDataByKey(Project project, String key) {
        List<TmsTest> testList = SlowUtils.runSyncWithProgress(project, "",
                () -> FileBasedIndex.getInstance().getValues(TMS_INDEX_KEY,
                        key,
                        GlobalSearchScope.projectScope(project))
        );
        if (!testList.isEmpty()) {
            return testList.getFirst();
        }
        return new TmsTest(key);
    }

    public static List<TmsTest> getAllTests(Project project) {
        return SlowUtils.runSyncWithProgress(project, "",
                (Supplier<? extends List<TmsTest>>) () -> {
                    Collection<String> allKeys = FileBasedIndex.getInstance().getAllKeys(TMS_INDEX_KEY, project);
                    List<TmsTest> allTests = new ArrayList<>();
                    for (String key : allKeys) {
                        allTests.addAll(FileBasedIndex.getInstance().getValues(TMS_INDEX_KEY,
                                key,
                                GlobalSearchScope.projectScope(project)));
                    }
                    return allTests;
                }
        );
    }
}
