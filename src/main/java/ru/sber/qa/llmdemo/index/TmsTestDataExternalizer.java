package ru.sber.qa.llmdemo.index;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

class TmsTestDataExternalizer implements DataExternalizer<TmsTest> {

    @Override
    public void save(@NotNull DataOutput out, TmsTest value) throws IOException {
        out.writeUTF(value.getKey());
        out.writeUTF(value.getTestLanguage().toString());

        // Сохраняем только стабильные идентификаторы
        if (value.getFilePath() != null) {
            out.writeUTF(value.getFilePath());
            out.writeInt(value.getOffset());
        } else {
            out.writeUTF("");
        }
    }

    @Override
    public TmsTest read(@NotNull DataInput in) throws IOException {
        String key = in.readUTF();
        String language = in.readUTF();
        String filePath = in.readUTF();

        if (filePath.isEmpty()) {
            return new TmsTest(key, TestLanguage.valueOf(language), null, -1);
        }

        int offset = in.readInt();
        return new TmsTest(key, TestLanguage.valueOf(language), filePath, offset);
    }
}
