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
import org.apache.camel.Exchange;
import org.apache.camel.Headers;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.jndi.JndiContext;

/**
 * Unit test of bean can propagate headers in a pipeline
 */
public class BeanPipelineTest extends ContextTestSupport {

    public void testBeanInPipeline() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World from James");
        mock.expectedHeaderReceived("from", "James");

        template.sendBodyAndHeader("direct:input", "Hello World", "from", "Claus");
        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:input").
                    pipeline("bean:foo", "bean:bar?method=usingExchange", "bean:baz").
                    to("mock:result");
            }
        };
    }

    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("foo", new FooBean());
        answer.bind("bar", new BarBean());
        answer.bind("baz", new BazBean());
        return answer;
    }

    public static class FooBean {
        public void onlyPlainBody(Object body) {
            assertEquals("Hello World", body);
        }
    }

    public static class BarBean {
        public void doNotUseMe(String body) {
            fail("Should not invoce me");
        }

        public void usingExchange(Exchange exchange) {
            String body = exchange.getIn().getBody(String.class);
            assertEquals("Hello World", body);
            assertEquals("Claus", exchange.getIn().getHeader("from"));
            exchange.getOut().setHeader("from", "James");
            exchange.getOut().setBody("Hello World from James");
        }
    }

    public static class BazBean {
        public void doNotUseMe(String body) {
            fail("Should not invoce me");
        }

        public void withAnnotations(@Headers Map<String, Object> headers, @Body String body) {
            assertEquals("Hello World from James", body);
            assertEquals("James", headers.get("from"));
        }
    }
}
