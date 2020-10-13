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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
public class RecipientListUseOriginalMessageIssueTest extends ContextTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data/inbox");
        deleteDirectory("target/data/outbox");
        super.setUp();
    }

    @Test
    public void testRecipientListUseOriginalMessageIssue() throws Exception {
        getMockEndpoint("mock:error").expectedMinimumMessageCount(1);

        template.sendBodyAndHeader("file:target/data/inbox", "A", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();

        File out = new File("target/data/outbox/hello.txt");
        String data = context.getTypeConverter().convertTo(String.class, out);
        assertEquals("A", data);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true).useOriginalMessage().to("file://target/data/outbox")
                        .to("mock:error");

                from("file://target/data/inbox?initialDelay=0&delay=10").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().setBody("B");
                    }
                }).process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // try to put some invalid destination
                        exchange.getIn().setHeader("path", "xxx");
                    }
                }).recipientList(header("path"));
            }
        };
    }
}
