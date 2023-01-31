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

import java.io.Serializable;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Timeout(30)
public class JmsInOutBeanReturnNullTest extends AbstractJMSTest {
    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testReturnBean() {
        MyBean out = template.requestBody("activemq:queue:JmsInOutBeanReturnNullTest", "Camel", MyBean.class);

        assertNotNull(out);
        assertEquals("Camel", out.getName());
    }

    @Test
    public void testReturnNull() {
        Object out = template.requestBody("activemq:queue:JmsInOutBeanReturnNullTest", "foo");
        assertNull(out);
    }

    @Test
    public void testReturnNullMyBean() {
        MyBean out = template.requestBody("activemq:queue:JmsInOutBeanReturnNullTest", "foo", MyBean.class);
        assertNull(out);
    }

    @Test
    public void testReturnNullExchange() {
        Exchange reply
                = template.request("activemq:queue:JmsInOutBeanReturnNullTest", exchange -> exchange.getIn().setBody("foo"));
        assertNotNull(reply);
        assertNotEquals("foo", reply.getMessage().getBody(), "There shouldn't be an out message");
        Message out = reply.getMessage();
        assertNotNull(out);
        Object body = out.getBody();
        assertNull(body, "Should be a null body");
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent setupComponent(CamelContext camelContext, ArtemisService service, String componentName) {
        final JmsComponent jmsComponent = super.setupComponent(camelContext, service, componentName);

        jmsComponent.setRequestTimeout(5000);

        return jmsComponent;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsInOutBeanReturnNullTest")
                        .bean(JmsInOutBeanReturnNullTest.class, "doSomething");
            }
        };
    }

    public MyBean doSomething(String body) {
        if ("foo".equals(body)) {
            return null;
        } else {
            return new MyBean(body);
        }
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }

    public static final class MyBean implements Serializable {

        private static final long serialVersionUID = 1L;
        public final String name;

        public MyBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
