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
package org.apache.camel.language;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class XPathRouteConcurrentTest extends ContextTestSupport {

    @Test
    public void testXPathNotConcurrent() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(0);

        template.sendBody("seda:foo", "<person><name>Claus</name></person>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testXPathTwoMessages() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(1);

        template.sendBody("seda:foo", "<person><name>Claus</name></person>");
        template.sendBody("seda:foo", "<person><name>James</name></person>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testXPathTwoMessagesNotSameTime() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(1);

        template.sendBody("seda:foo", "<person><name>Claus</name></person>");

        Thread.sleep(10);

        template.sendBody("seda:foo", "<person><name>James</name></person>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNoConcurrent() throws Exception {
        doSendMessages(1);
    }

    @Test
    public void testConcurrent() throws Exception {
        doSendMessages(10);
    }

    private void doSendMessages(int files) throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(files);
        getMockEndpoint("mock:result").expectsNoDuplicates(body());
        getMockEndpoint("mock:other").expectedMessageCount(0);

        for (int i = 0; i < files; i++) {
            template.sendBody("seda:foo", "<person><id>" + i + "</id><name>Claus</name></person>");
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo?concurrentConsumers=10").choice().when().xpath("/person/name = 'Claus'").to("mock:result").otherwise().to("mock:other").end();

            }
        };
    }
}
