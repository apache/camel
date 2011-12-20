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
package org.apache.camel.component.ref;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;

/**
 * @version 
 */
public class RefFileEndpointTest extends ContextTestSupport {

    private SimpleRegistry registry = new SimpleRegistry();

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/foo");
        super.setUp();
    }

    public void testRefFileEndpoint() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("file:target/foo", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();

        FileConsumer consumer = (FileConsumer) context.getRoute("foo").getConsumer();
        assertEquals(3000, consumer.getDelay());
        assertEquals(250, consumer.getInitialDelay());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = new DefaultCamelContext(registry);
        registry.put("foo", context.getEndpoint("file:target/foo?initialDelay=250&delay=3000&delete=true"));
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("ref:foo").routeId("foo").to("mock:result");
            }
        };
    }
}
