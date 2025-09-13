package ru.sber.qa.llmdemo.utils;

import lombok.experimental.UtilityClass;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

@UtilityClass
public class MdUtils {

    public static String getCodeBlock(String responseLlm) {
        Parser parser = Parser.builder().build();
        Node document = parser.parse(responseLlm);

        final String[] finCodeBlock = {""};
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(FencedCodeBlock blockQuote) {
                if ("java".equals(blockQuote.getInfo())) {
                    finCodeBlock[0] = blockQuote.getLiteral();

                }
            }
        });
        return finCodeBlock[0];
    }
}
