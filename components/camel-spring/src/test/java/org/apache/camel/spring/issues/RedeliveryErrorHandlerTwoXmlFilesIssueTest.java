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
package org.apache.camel.spring.issues;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 *
 */
public class RedeliveryErrorHandlerTwoXmlFilesIssueTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/issues/RedeliveryErrorHandlerTwoXmlFilesIssueTest-1.xml",
                "org/apache/camel/spring/issues/RedeliveryErrorHandlerTwoXmlFilesIssueTest-2.xml");
    }

    @Test
    public void testRedeliveryErrorHandlerTwoXmlFilesIssue() throws Exception {
        getMockEndpoint("mock:handled").expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        OnRedeliveryCounter counter = context.getRegistry().lookupByNameAndType("counter", OnRedeliveryCounter.class);
        assertEquals(3, counter.getCounter());
    }

    public static class OnRedeliveryCounter implements Processor {

        private int counter;

        @Override
        public void process(Exchange exchange) throws Exception {
            counter++;
        }

        public int getCounter() {
            return counter;
        }
    }

}
