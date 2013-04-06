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
package org.apache.camel.itest.idempotent;

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class IdempotentConsumerTest extends CamelSpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/itest/idempotent/IdempotentConsumerTest.xml");
    }

    @Test
    public void testDuplicateMessagesAreFilteredOut() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("one", "two", "three");

        template.sendBodyAndHeader("direct:start", "one", "messageId", 1);
        template.sendBodyAndHeader("direct:start", "two", "messageId", 2);
        template.sendBodyAndHeader("direct:start", "one", "messageId", 1);
        template.sendBodyAndHeader("direct:start", "two", "messageId", 2);
        template.sendBodyAndHeader("direct:start", "three", "messageId", 3);

        assertMockEndpointsSatisfied();
    }

}
