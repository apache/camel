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
package org.apache.camel.spring.processor;

import org.apache.camel.spring.SpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringSwitchTest extends SpringTestSupport {
    @Test
    public void caseAndFallbackUseSeparateRoutes() throws Exception {
        getMockEndpoint("mock:billing").expectedBodiesReceived("invoice");
        getMockEndpoint("mock:technical").expectedBodiesReceived("outage");
        getMockEndpoint("mock:review").expectedBodiesReceived("unknown", "missing");
        getMockEndpoint("mock:after").expectedMessageCount(4);
        template.sendBodyAndHeader("direct:start", "invoice", "department", "BILLING");
        template.sendBodyAndHeader("direct:start", "outage", "department", "technical");
        template.sendBodyAndHeader("direct:start", "unknown", "department", "other");
        template.sendBody("direct:start", "missing");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void xpathSelectorUsesSpringNamespaces() throws Exception {
        String body = "<t:ticket xmlns:t='urn:tickets'><t:department>billing</t:department></t:ticket>";
        getMockEndpoint("mock:billing").expectedBodiesReceived(body);
        getMockEndpoint("mock:review").expectedMessageCount(0);
        template.sendBody("direct:xpath", body);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/processor/SpringSwitchTest.xml");
    }
}
