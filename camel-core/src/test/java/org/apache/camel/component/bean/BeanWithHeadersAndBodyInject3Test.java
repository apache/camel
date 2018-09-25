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

import java.util.Map;

import javax.naming.Context;

import org.apache.camel.Body;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Headers;
import org.apache.camel.OutHeaders;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.jndi.JndiContext;
import org.junit.Test;

/**
 * @version 
 */
public class BeanWithHeadersAndBodyInject3Test extends ContextTestSupport {
    private MyBean myBean = new MyBean();

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
        end.message(0).header("out").isNull();

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
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("myBean", myBean);
        return answer;
    }

    public static class MyBean {

        public String doSomething(@Body String body, @Headers Map<?, ?> headers,
                                  @OutHeaders Map<String, Object> outHeaders) {
            if (outHeaders != null) {
                outHeaders.put("out", 123);
            }

            return "Hello!";
        }
    }
}
