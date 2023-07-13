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
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class FileConsumerMoveFailureOnCompletionTest extends ContextTestSupport {

    @Test
    public void testMoveFailedRollbackOnly() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.expectedFileExists(testFile("error/bye-error.txt"), "Kaboom");

        getMockEndpoint("mock:failed").expectedMessageCount(1);

        template.sendBodyAndHeader(fileUri(), "Kaboom", Exchange.FILE_NAME, "bye.txt");

        mock.assertIsSatisfied(1000);
    }

    @Test
    public void testMoveFailedCommitAndFailure() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedFileExists(testFile(".camel/hello.txt"), "Hello World");
        mock.expectedFileExists(testFile("error/bye-error.txt"), "Kaboom");

        getMockEndpoint("mock:failed").expectedMessageCount(1);

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(fileUri(), "Kaboom", Exchange.FILE_NAME, "bye.txt");

        mock.assertIsSatisfied(1000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("?initialDelay=0&delay=10&moveFailed=error/${file:name.noext}-error.txt"))
                        .onCompletion().onFailureOnly().to("mock:failed").end()
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                String body = exchange.getIn().getBody(String.class);
                                if ("Kaboom".equals(body)) {
                                    throw new IllegalArgumentException("Forced");
                                }
                            }
                        }).convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
