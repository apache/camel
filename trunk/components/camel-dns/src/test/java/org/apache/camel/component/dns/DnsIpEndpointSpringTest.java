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
package org.apache.camel.component.dns;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * A series of tests to check the IP lookup operation.
 */
public class DnsIpEndpointSpringTest extends CamelSpringTestSupport {

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Test
    public void testNullIPRequests() throws Exception {
        resultEndpoint.expectedMessageCount(0);
        try {
            template.sendBodyAndHeader("hello", "dns.domain", null);
            fail("Should have thrown exception");
        } catch (Throwable t) {
            assertTrue(t.getCause() instanceof IllegalArgumentException);
        }
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testEmptyIPRequests() throws Exception {
        resultEndpoint.expectedMessageCount(0);
        try {
            template.sendBodyAndHeader("hello", "dns.domain", "");
            fail("Should have thrown exception");
        } catch (Throwable t) {
            assertTrue(t.getCause() instanceof IllegalArgumentException);
        }
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    @Ignore("Run manually, performs DNS lookup to remote apache.org server")
    public void testValidIPRequests() throws Exception {
        resultEndpoint.expectedMessageCount(1);

        resultEndpoint.expectedBodiesReceived("140.211.11.131");

        template.sendBodyAndHeader("hello", "dns.domain", "www.apache.org");
        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("IPCheck.xml");
    }
}
