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
package org.apache.camel.language.groovy;

import groovy.lang.GroovyClassLoader;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class GroovyTest extends ContextTestSupport {
    protected String expected = "<hello>world!</hello>";
    protected String groovyBuilderClass = "org.apache.camel.language.groovy.example.GroovyRoutes";

    public void testSendMatchingMessage() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:results");
        resultEndpoint.expectedBodiesReceived(expected);

        template.sendBodyAndHeader("direct:a", expected, "foo", "bar");

        assertMockEndpointsSatisifed();

        log.debug("Should have received one exchange: " + resultEndpoint.getReceivedExchanges());
    }

    public void testSendNotMatchingMessage() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:results");
        resultEndpoint.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:a", expected, "foo", "123");

        assertMockEndpointsSatisifed();

        log.debug("Should not have received any messages: " + resultEndpoint.getReceivedExchanges());
    }


    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext answer = super.createCamelContext();

        GroovyClassLoader classLoader = new GroovyClassLoader();
        Class<?> type = classLoader.loadClass(groovyBuilderClass);
        Object object = answer.getInjector().newInstance(type);
        RouteBuilder builder = assertIsInstanceOf(RouteBuilder.class, object);

        log.info("Loaded builder: " + builder);
        answer.addRoutes(builder);

        return answer;
    }
}