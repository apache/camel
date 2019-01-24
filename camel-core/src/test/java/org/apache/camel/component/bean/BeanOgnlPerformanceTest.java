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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.StopWatch;
import org.junit.Test;

/**
 *
 */
public class BeanOgnlPerformanceTest extends ContextTestSupport {

    private int size = 1000;
    private String cache = "true";

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("foo", new MyFooBean());
        return jndi;
    }

    @Test
    public void testBeanOgnlPerformance() throws Exception {
        StopWatch watch = new StopWatch();

        getMockEndpoint("mock:result").expectedMessageCount(size);

        for (int i = 0; i < size; i++) {
            template.sendBody("direct:start", "Hello World");
        }

        assertMockEndpointsSatisfied();

        log.info("Took {} millis", watch.taken());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .toF("bean:foo?cache=%s&method=hello('Camel')", cache)
                    .toF("bean:foo?cache=%s&method=hello('Camel')", cache)
                    .toF("bean:foo?cache=%s&method=hello('Camel')", cache)
                    .toF("bean:foo?cache=%s&method=hello('Camel')", cache)
                    .toF("bean:foo?cache=%s&method=hello('Camel')", cache)
                    .toF("bean:foo?cache=%s&method=hello('Camel')", cache)
                    .toF("bean:foo?cache=%s&method=hello('Camel')", cache)
                    .toF("bean:foo?cache=%s&method=hello('Camel')", cache)
                    .toF("bean:foo?cache=%s&method=hello('Camel')", cache)
                    .toF("bean:foo?cache=%s&method=hello('Camel')", cache)
                    .to("mock:result");
            }
        };
    }
}
