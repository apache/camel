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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BeanInfoCacheExchangeTest extends ContextTestSupport {

    @Test
    public void testBeanInfoCacheExchange() throws Exception {
        BeanComponent bean = context.getComponent("bean", BeanComponent.class);

        Assertions.assertEquals(0, bean.getCurrentBeanCacheSize());

        getMockEndpoint("mock:result").expectedBodiesReceived("123", "123", "123");
        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");
        template.sendBody("direct:start", "Hi Moon");
        assertMockEndpointsSatisfied();

        // only cache the exchange class (and not instances)
        Assertions.assertEquals(1, bean.getCurrentBeanCacheSize());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setProperty("myKey", constant("123"))
                        .setBody(simple("${exchange.getProperty(\"myKey\")}"))
                        .to("mock:result");
            }
        };
    }

}
