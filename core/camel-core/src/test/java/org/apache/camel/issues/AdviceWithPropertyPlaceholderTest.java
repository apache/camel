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
package org.apache.camel.issues;

import java.util.Properties;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.PropertiesComponent;
import org.junit.jupiter.api.Test;

public class AdviceWithPropertyPlaceholderTest extends ContextTestSupport {

    @Test
    public void testAdvicePropertyPlaceholder() throws Exception {
        Properties props = new Properties();
        props.put("myPattern", "seda*");
        props.put("myEnd", "mock:result");
        PropertiesComponent pc = context.getPropertiesComponent();
        pc.setInitialProperties(props);

        AdviceWith.adviceWith(context, null, r -> {
            r.mockEndpointsAndSkip("{{myPattern}}");
            r.weaveAddLast().to("{{myEnd}}");
        });

        getMockEndpoint("mock:seda:a").expectedMessageCount(1);
        getMockEndpoint("mock:seda:b").expectedMessageCount(1);
        getMockEndpoint("mock:seda:c").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("seda:a")
                        .to("seda:b")
                        .to("seda:c");
            }
        };
    }

}
