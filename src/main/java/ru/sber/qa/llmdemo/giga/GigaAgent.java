package ru.sber.qa.llmdemo.giga;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface GigaAgent {

    @SystemMessage(fromResource = "prompts/system_prompt.md")
    String generateTest(@MemoryId String testKey,
                        @V("definitions") String similarSteps,
                        @V("javaVersion") String sdkVersion,
                        @V("testExamples") String testExamples,
                        @V("additional") String additional,
                        @UserMessage String firstGenMessage);

    @UserMessage("""
            Текущий код теста:
            ```java
            {{currentCode}}
            ```
            Текущий запрос:
            {{userRequest}}
            """)
    String refineGeneratedTest(@MemoryId String testKey,
                               @V("userRequest") String userRequest,
                               @V("currentCode") String currentCode);

}
