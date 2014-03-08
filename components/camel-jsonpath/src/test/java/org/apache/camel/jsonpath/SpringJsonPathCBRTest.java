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
package org.apache.camel.jsonpath;

import java.io.File;

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringJsonPathCBRTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/jsonpath/SpringJsonPathCBTTest.xml");
    }

    @Test
    public void testCheap() throws Exception {
        getMockEndpoint("mock:cheap").expectedMessageCount(1);
        getMockEndpoint("mock:average").expectedMessageCount(0);
        getMockEndpoint("mock:expensive").expectedMessageCount(0);

        template.sendBody("direct:start", new File("src/test/resources/cheap.json"));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAverage() throws Exception {
        getMockEndpoint("mock:cheap").expectedMessageCount(0);
        getMockEndpoint("mock:average").expectedMessageCount(1);
        getMockEndpoint("mock:expensive").expectedMessageCount(0);

        template.sendBody("direct:start", new File("src/test/resources/average.json"));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExpensive() throws Exception {
        getMockEndpoint("mock:cheap").expectedMessageCount(0);
        getMockEndpoint("mock:average").expectedMessageCount(0);
        getMockEndpoint("mock:expensive").expectedMessageCount(1);

        template.sendBody("direct:start", new File("src/test/resources/expensive.json"));

        assertMockEndpointsSatisfied();
    }
}
