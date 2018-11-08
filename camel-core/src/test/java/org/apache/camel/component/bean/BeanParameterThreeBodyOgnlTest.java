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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

/**
 *
 */
public class BeanParameterThreeBodyOgnlTest extends ContextTestSupport {

    @Test
    public void testBeanParameterValue() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("3");

        List<String> body = new ArrayList<>();
        body.add("A");
        body.add("B");
        body.add("C");
        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("foo", new MyBean());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("bean:foo?method=bar(${body[0]},${body[1]},${body[2]})")
                    .to("mock:result");
            }
        };
    }

    public static class MyBean {

        public String bar(String order1, String order2, String order3) {
            assertEquals("A", order1);
            assertEquals("B", order2);
            assertEquals("C", order3);

            return "3";
        }

        public String bar(String order1, String order2) {
            assertEquals("A", order1);
            assertEquals("B", order2);
            return "2";
        }

        public String bar(String order1) {
            assertEquals("A", order1);
            return "1";
        }
    }

}
