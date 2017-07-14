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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class FileConcurrentWriteAppendSameFileTest extends ContextTestSupport {

    private final int size = 100;

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/concurrent");
        super.setUp();
    }

    public void testConcurrentAppend() throws Exception {
        // create file with many lines
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append("Line " + i + LS);
        }

        template.sendBodyAndHeader("file:target/concurrent", sb.toString(), Exchange.FILE_NAME, "input.txt");

        // start route
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(size);
        mock.expectsNoDuplicates(body());
        mock.setResultWaitTime(30000);

        // we need to wait a bit for our slow CI server to make sure the entire file is written on disc
        Thread.sleep(500);
        context.startRoute("foo");

        assertMockEndpointsSatisfied();

        // check the file has correct number of lines
        String txt = context.getTypeConverter().convertTo(String.class, new File("target/concurrent/outbox/result.txt"));
        assertNotNull(txt);

        String[] lines = txt.split(LS);
        assertEquals("Should be " + size + " lines", size, lines.length);

        // should be unique
        Set<String> rows = new LinkedHashSet<String>(Arrays.asList(lines));
        assertEquals("Should be " + size + " unique lines", size, rows.size());

        log.info(txt);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/concurrent?initialDelay=0&delay=10").routeId("foo").noAutoStartup()
                    .split(body().tokenize(LS)).parallelProcessing().streaming()
                        .setBody(body().append(":Status=OK").append(LS))
                        .to("file:target/concurrent/outbox?fileExist=Append&fileName=result.txt")
                        .to("mock:result")
                    .end();
            }
        };
    }
}
