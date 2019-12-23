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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class RecipientListUseOriginalMessageEndpointExceptionIssueTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/inbox");
        deleteDirectory("target/data/outbox");
        super.setUp();
    }

    @Test
    public void testRecipientListUseOriginalMessageIssue() throws Exception {
        getMockEndpoint("mock:throwException").whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                throw new Exception("Exception raised");
            }
        });
        getMockEndpoint("mock:error").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:error").expectedFileExists("target/data/outbox/hello.txt", "A");

        template.sendBodyAndHeader("file:target/data/inbox", "A", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true).useOriginalMessage().to("file://target/data/outbox").to("mock:error");

                from("file://target/data/inbox?initialDelay=0&delay=10").transform(constant("B")).setHeader("path", constant("mock:throwException"))
                    // must enable share uow to let the onException use
                    // the original message from the route input
                    .recipientList(header("path")).shareUnitOfWork();
            }
        };
    }
}
