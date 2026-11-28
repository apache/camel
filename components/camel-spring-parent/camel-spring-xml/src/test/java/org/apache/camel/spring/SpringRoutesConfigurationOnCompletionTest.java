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
package org.apache.camel.spring;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * CAMEL-25420: an onCompletion of a route configuration in Spring XML fires once per exchange, as with the Java DSL,
 * when the exchange goes through several routes that the configuration applies to.
 */
public class SpringRoutesConfigurationOnCompletionTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/spring/SpringRoutesConfigurationOnCompletionTest.xml");
    }

    @Test
    public void testGlobalConfigurationFiresOnce() throws Exception {
        getMockEndpoint("mock:global").expectedMessageCount(1);
        getMockEndpoint("mock:named").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:global", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNamedConfigurationOnBothRoutesFiresOnce() throws Exception {
        getMockEndpoint("mock:global").expectedMessageCount(0);
        getMockEndpoint("mock:named").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:named", "Bye World");

        assertMockEndpointsSatisfied();
    }
}
