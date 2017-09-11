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
package org.apache.camel.issues;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class RecipientListUseOriginalMessageIssueTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/inbox");
        deleteDirectory("target/outbox");
        super.setUp();
    }

    public void testRecipientListUseOriginalMessageIssue() throws Exception {
        getMockEndpoint("mock:error").expectedMinimumMessageCount(1);

        template.sendBodyAndHeader("file:target/inbox", "A", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();

        File out = new File("target/outbox/hello.txt");
        String data = context.getTypeConverter().convertTo(String.class, out);
        assertEquals("A", data);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class)
                    .handled(true).useOriginalMessage()
                    .to("file://target/outbox")
                    .to("mock:error");

                from("file://target/inbox?initialDelay=0&delay=10").process(new Processor() {
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

