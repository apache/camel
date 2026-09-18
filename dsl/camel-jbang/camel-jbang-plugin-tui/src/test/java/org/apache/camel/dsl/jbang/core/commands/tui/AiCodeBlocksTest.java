/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCodeBlocksTest {

    @Test
    void parsesFencedBlocksWithoutTheFences() {
        String answer = """
                Here is the route:

                ```yaml
                - route:
                    from:
                      uri: timer:tick
                ```

                And the same in Java:

                ```java
                from("timer:tick").log("tick");
                ```
                Done.
                """;

        List<AiCodeBlocks.CodeBlock> blocks = AiCodeBlocks.parse(answer);

        assertEquals(2, blocks.size());
        assertEquals("yaml", blocks.get(0).language());
        assertEquals("- route:\n    from:\n      uri: timer:tick", blocks.get(0).code());
        assertEquals(3, blocks.get(0).lineCount());
        assertEquals("yaml  3 lines", blocks.get(0).label());
        assertEquals("- route:", blocks.get(0).preview(40));
        assertEquals("java", blocks.get(1).language());
        assertEquals("from(\"timer:tick\").log(\"tick\");", blocks.get(1).code());
        assertEquals("java  1 line", blocks.get(1).label());
    }

    @Test
    void handlesTildeFencesLanguageAttributesAndNoLanguage() {
        String answer = "~~~properties title=app\ncamel.main.name=foo\n~~~\n```\nplain\n```\n";

        List<AiCodeBlocks.CodeBlock> blocks = AiCodeBlocks.parse(answer);

        assertEquals(2, blocks.size());
        assertEquals("properties", blocks.get(0).language());
        assertEquals("camel.main.name=foo", blocks.get(0).code());
        assertEquals("", blocks.get(1).language());
        assertEquals("code  1 line", blocks.get(1).label());
    }

    @Test
    void keepsAnUnterminatedBlockAndIgnoresInlineBackticks() {
        String answer = "Use `camel run` like this:\n```bash\ncamel run foo.yaml\n";

        List<AiCodeBlocks.CodeBlock> blocks = AiCodeBlocks.parse(answer);

        assertEquals(1, blocks.size());
        assertEquals("bash", blocks.get(0).language());
        assertEquals("camel run foo.yaml", blocks.get(0).code());
        assertTrue(AiCodeBlocks.parse("no code here, only `inline` text").isEmpty());
        assertTrue(AiCodeBlocks.parse(null).isEmpty());
    }
}
