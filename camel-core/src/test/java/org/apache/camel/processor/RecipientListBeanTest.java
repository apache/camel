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
package org.apache.camel.processor;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version 
 */
public class RecipientListBeanTest extends ContextTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myBean", new MyBean());
        return jndi;
    }

    public void testRecipientListWithBean() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello c");

        String out = template.requestBody("direct:start", "direct:a,direct:b,direct:c", String.class);
        assertEquals("Hello c", out);

        assertMockEndpointsSatisfied();
    }

    // @Ignore("CAMEL-4894") @Test
    public void fixmeTestRecipientListWithParams() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello b");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("one", 21);
        headers.put("two", "direct:a,direct:b,direct:c");

        String out = template.requestBodyAndHeaders("direct:params", "Hello World", headers, String.class);
        assertEquals("Hello b", out);

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").recipientList(bean("myBean", "foo")).to("mock:result");
                from("direct:params").recipientList(bean("myBean", "bar(header.one, header.two)"), ",").to("mock:result");

                from("direct:a").transform(constant("Hello a"));
                from("direct:b").transform(constant("Hello b"));
                from("direct:c").transform(constant("Hello c"));
            }
        };
    }

    public class MyBean {

        public String[] foo(String body) {
            return body.split(",");
        }

        public String foo(int one, String two) {
            String [] recipients = two.split(",");
            int count = Math.min(one, recipients.length);
            StringBuilder answer = new StringBuilder();
            for (int i = 0; i < count; i++) {
                answer.append(i > 0 ? "," : "");
                answer.append(recipients[i]);
            }
            return answer.toString();
        }
    }

}