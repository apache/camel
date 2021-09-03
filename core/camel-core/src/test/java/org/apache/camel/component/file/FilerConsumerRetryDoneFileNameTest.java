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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

/**
 * Unit test done files with retry
 */
public class FilerConsumerRetryDoneFileNameTest extends ContextTestSupport {

    @Test
    public void testDoneFile() throws Exception {
        getMockEndpoint("mock:input").expectedMessageCount(2);
        getMockEndpoint("mock:input").expectedFileExists(testFile(".camel/hello.txt"));

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(fileUri(), "", Exchange.FILE_NAME, "done");

        assertMockEndpointsSatisfied();

        // done file should be deleted now
        assertFileNotExists(testFile("done"));

        // as well the original file should be moved to .camel
        assertFileExists(testFile(".camel/hello.txt"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("?doneFileName=done&initialDelay=0&delay=10")).to("mock:input")
                        .process(new Processor() {
                            int index;

                            @Override
                            public void process(Exchange exchange) throws Exception {
                                if (index++ == 0) {
                                    // done file should still exists
                                    assertFileExists(testFile("done"));
                                    throw new IllegalArgumentException("Forced");
                                }
                            }
                        });
            }
        };
    }

}
