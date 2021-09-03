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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class FileConsumeSimpleRelativeMoveToAbsoluteTest extends ContextTestSupport {

    @Test
    public void testMoveToSubDir() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        // will flatten when using absolute path in move
        mock.expectedFileExists(testFile(".done/bye.txt"));
        mock.expectedFileExists(testFile(".done/hello.txt"));
        mock.expectedFileExists(testFile(".done/goodday.txt"));

        template.sendBodyAndHeader(fileUri(), "Bye World", Exchange.FILE_NAME, "bye.txt");
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "sub/hello.txt");
        template.sendBodyAndHeader(fileUri(), "Goodday World", Exchange.FILE_NAME, "sub/sub2/goodday.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        String base = testDirectory(".done").toAbsolutePath().toString();
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("?recursive=true&move=" + base + "&initialDelay=0&delay=10"))
                        .convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
