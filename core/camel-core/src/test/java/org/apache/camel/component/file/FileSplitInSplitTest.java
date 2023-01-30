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
package org.apache.camel.component.file;

import java.nio.file.Files;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.language.SimpleExpression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FileSplitInSplitTest extends ContextTestSupport {

    private final int size = 3;
    private final String comma = ",";

    @Test
    public void testConcurrentAppend() throws Exception {
        // create file with many lines
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append("Block1 Line ").append(i).append(LS);
        }
        sb.append(comma);
        for (int i = 10; i < size + 10; i++) {
            sb.append("Block2 Line ").append(i).append(LS);
        }

        template.sendBodyAndHeader(fileUri(), sb.toString(), Exchange.FILE_NAME, "input.txt");

        // start route
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();

        // check one file has expected number of lines +1 saying split is
        // complete.
        assertFileExists(testFile("outbox/result0.txt"));
        String txt = new String(Files.readAllBytes(testFile("outbox/result0.txt")));
        assertNotNull(txt);

        String[] lines = txt.split(LS);
        assertEquals(size + 1, lines.length, "Should be " + (size + 1) + " lines");

        txt = new String(Files.readAllBytes(testFile("outbox/result1.txt")));
        assertNotNull(txt);

        lines = txt.split(LS);
        assertEquals(size + 1, lines.length, "Should be " + (size + 1) + " lines");

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("?initialDelay=0&delay=10")).routeId("foo").noAutoStartup()
                        .split(body().tokenize(comma)).parallelProcessing().streaming()
                        .setProperty("split", new SimpleExpression("${exchangeProperty.CamelSplitIndex}"))
                        .split(body().tokenize(LS)).parallelProcessing().streaming()
                        .setBody(body().append(":Status=OK").append(LS))
                        .to(fileUri("outbox?fileExist=Append&fileName=result${exchangeProperty.split}.txt")).end()
                        .setBody(new SimpleExpression("${exchangeProperty.split} complete"))
                        .to(fileUri("outbox?fileExist=Append&fileName=result${exchangeProperty.split}.txt")).end()
                        .to("mock:result");

            }
        };
    }

}
