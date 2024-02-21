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

import java.util.Map;

import org.apache.camel.Body;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Headers;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BeanWithHeadersAndBodyInject3Test extends ContextTestSupport {
    private final MyBean myBean = new MyBean();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to("bean:myBean?method=doSomething").to("mock:finish");
            }
        };
    }

    @Test
    public void testInOnly() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:finish");
        end.expectedBodiesReceived("Hello!");

        sendBody("direct:start", "Test Input");

        assertMockEndpointsSatisfied();

        assertNotNull(end.getExchanges().get(0).getIn().getBody());
        assertEquals("Hello!", end.getExchanges().get(0).getIn().getBody());
    }

    @Test
    public void testInOut() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:finish");
        end.expectedBodiesReceived("Hello!");
        end.expectedHeaderReceived("out", 123);

        String out = template.requestBody("direct:start", "Test Input", String.class);
        assertEquals("Hello!", out);

        assertMockEndpointsSatisfied();

        assertNotNull(end.getExchanges().get(0).getIn().getBody());
        assertEquals("Hello!", end.getExchanges().get(0).getIn().getBody());
        assertEquals(123, end.getExchanges().get(0).getIn().getHeader("out"));
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        answer.bind("myBean", myBean);
        return answer;
    }

    public static class MyBean {

        public String doSomething(@Body String body, @Headers Map<String, Object> headers) {
            headers.put("out", 123);
            return "Hello!";
        }
    }
}
