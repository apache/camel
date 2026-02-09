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

import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringSimplePrettyTest extends SpringTestSupport {

    private static final String XML = """
            <person>
              <name>
                Jack
              </name>
            </person>""";

    private static final String JSON = """
            {
            	"name": "Jack",
            	"age": 44
            }
            """;

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/SpringSimplePrettyTest.xml");
    }

    @Test
    public void testPrettyXml() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(XML);

        template.sendBody("direct:xml", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPrettyJSon() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(JSON);

        template.sendBody("direct:json", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPrettyText() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:text", "World");

        assertMockEndpointsSatisfied();
    }
}
