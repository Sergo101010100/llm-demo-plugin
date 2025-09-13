package ru.sber.qa.llmdemo.index;

import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

public class TmsIndexExtension extends FileBasedIndexExtension<String, TmsTest> {
    static final ID<String, TmsTest> TMS_INDEX_KEY = ID.create("testsync.index");
    private static final int BASE_VERSION = 4;


    @Override
    public @NotNull ID<String, TmsTest> getName() {
        return TMS_INDEX_KEY;
    }

    @Override
    public @NotNull DataIndexer<String, TmsTest, FileContent> getIndexer() {
        return new TmsTestFileContentDataIndexer();
    }

    @Override
    public @NotNull KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public @NotNull DataExternalizer<TmsTest> getValueExternalizer() {
        return new TmsTestDataExternalizer();
    }

    @Override
    public int getVersion() {
        return BASE_VERSION;
    }

    @Override
    public FileBasedIndex.@NotNull InputFilter getInputFilter() {
        return file ->
                "java".equals(file.getExtension());
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }
}
