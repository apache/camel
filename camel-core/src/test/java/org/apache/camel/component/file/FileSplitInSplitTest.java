/**
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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.processor.interceptor.Tracer;

public class FileSplitInSplitTest extends ContextTestSupport {

    private final int size = 3;
    private final String comma = ",";

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/split");
        super.setUp();
    }

    public void testConcurrentAppend() throws Exception {
        // create file with many lines
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append("Block1 Line " + i + LS);
        }
        sb.append(comma);
        for (int i = 10; i < size + 10; i++) {
            sb.append("Block2 Line " + i + LS);
        }

        template.sendBodyAndHeader("file:target/split", sb.toString(), Exchange.FILE_NAME, "input.txt");

        // start route
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        context.startRoute("foo");
        
        assertMockEndpointsSatisfied();

        // check one file has expected number of lines +1 saying split is
        // complete.
        String txt = context.getTypeConverter().convertTo(String.class,
                                                          new File("target/split/outbox/result0.txt"));
        assertNotNull(txt);

        String[] lines = txt.split(LS);
        assertEquals("Should be " + (size + 1) + " lines", size + 1, lines.length);
        
        
        txt = context.getTypeConverter().convertTo(String.class,
                                                          new File("target/split/outbox/result1.txt"));
        assertNotNull(txt);

        lines = txt.split(LS);
        assertEquals("Should be " + (size + 1) + " lines", size + 1, lines.length);

        
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Tracer tracer = new Tracer();
                getContext().addInterceptStrategy(tracer);
                
                from("file:target/split?initialDelay=0&delay=10").routeId("foo").noAutoStartup()
                    .split(body().tokenize(comma))
                        .parallelProcessing()
                        .streaming()
                        .setProperty("split", new SimpleExpression("${property.CamelSplitIndex}"))
                        .split(body().tokenize(LS))
                            .parallelProcessing()
                            .streaming()
                            .setBody(body().append(":Status=OK").append(LS))
                            .to("file:target/split/outbox?fileExist=Append&fileName=result${property.split}.txt")
                       .end()
                       .setBody(new SimpleExpression("${property.split} complete"))
                       .to("file:target/split/outbox?fileExist=Append&fileName=result${property.split}.txt")
                    .end().to("mock:result");

            }
        };
    }

}
