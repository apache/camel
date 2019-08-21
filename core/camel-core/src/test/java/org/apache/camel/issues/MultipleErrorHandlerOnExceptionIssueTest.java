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
package org.apache.camel.issues;

import java.io.IOException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class MultipleErrorHandlerOnExceptionIssueTest extends ContextTestSupport {

    @Test
    public void testMultipleErrorHandlerOnExceptionA() throws Exception {
        getMockEndpoint("mock:handled").expectedMessageCount(1);
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:dead.a").expectedMessageCount(0);
        getMockEndpoint("mock:dead.b").expectedMessageCount(0);

        template.sendBody("seda:a", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMultipleErrorHandlerOnExceptionB() throws Exception {
        getMockEndpoint("mock:handled").expectedMessageCount(0);
        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:dead.a").expectedMessageCount(0);
        getMockEndpoint("mock:dead.b").expectedMessageCount(1);

        template.sendBody("seda:b", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalArgumentException.class).handled(true).to("mock:handled");

                from("seda:a")
                    .errorHandler(deadLetterChannel("mock:dead.a").maximumRedeliveries(3).redeliveryDelay(0).retryAttemptedLogLevel(LoggingLevel.WARN).asyncDelayedRedelivery())
                    .to("mock:a").throwException(new IllegalArgumentException("Forced A"));

                from("seda:b").errorHandler(deadLetterChannel("mock:dead.b").maximumRedeliveries(2).redeliveryDelay(0).retryAttemptedLogLevel(LoggingLevel.WARN)).to("mock:b")
                    .throwException(new IOException("Some IO error"));
            }
        };
    }
}
