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
package org.apache.camel.component.jms;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DynamicRouteTest extends AbstractJMSTest {

    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    @BindToRegistry
    private final MyBean myBean = new MyBean();
    private final String componentName = "jms";
    protected ProducerTemplate template;

    @Test
    void testDynamicRouteWithJms() {
        String response = template.requestBody("jms:queue:request?replyTo=bar", "foo", String.class);
        assertEquals("response is foo", response);
        response = template.requestBody("jms:queue:request", "bar", String.class);
        assertEquals("response is bar", response);

    }

    @Test
    void testDynamicRouteWithDirect() {
        String response = template.requestBody("direct:start", "foo", String.class);
        assertEquals("response is foo", response);
        response = template.requestBody("direct:start", "bar", String.class);
        assertEquals("response is bar", response);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            public void configure() {
                from("jms:queue:request")
                        .dynamicRouter().method(MyDynamicRouter.class, "route");
                from("direct:start")
                        .dynamicRouter(method(new MyDynamicRouter()));
            }

        };
    }

    public static class MyBean {

        public String foo() {
            return "response is foo";
        }

        public String bar() {
            return "response is bar";
        }
    }

    public static class MyDynamicRouter {
        public String route(String methodName, @Header(Exchange.SLIP_ENDPOINT) String previous) {

            if (previous != null && previous.startsWith("bean://myBean?method")) {
                // we get the result here and stop routing
                return null;
            } else {
                return "bean:myBean?method=" + methodName;
            }
        }
    }

    @Override
    protected String getComponentName() {
        return componentName;
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        template = camelContextExtension.getProducerTemplate();
    }

}
