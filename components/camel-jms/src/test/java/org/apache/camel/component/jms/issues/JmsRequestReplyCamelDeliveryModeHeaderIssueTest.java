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

package org.apache.camel.component.jms.issues;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.jms.JmsConstants;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class JmsRequestReplyCamelDeliveryModeHeaderIssueTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    protected CamelContext context;
    protected ProducerTemplate template;

    @Test
    public void testInOnly() {
        Exchange out = template.request(context.getEndpoint("activemq:cheese"), (e) -> {
            e.setPattern(ExchangePattern.InOnly);
            e.getMessage().setBody("Camel");
        });
        Assertions.assertNotNull(out);
        Assertions.assertEquals("Camel", out.getMessage().getBody());
        Assertions.assertEquals(1, out.getMessage().getHeaders().size());
        Assertions.assertEquals(
                "cheese", out.getMessage().getHeaders().get(JmsConstants.JMS_DESTINATION_NAME_PRODUCED));
        Assertions.assertNull(out.getMessage().getHeaders().get(JmsConstants.JMS_DELIVERY_MODE));
    }

    @Test
    public void testInOut() {
        Exchange out = template.request(context.getEndpoint("activemq:cheese"), (e) -> {
            e.setPattern(ExchangePattern.InOut);
            e.getMessage().setBody("World");
        });
        Assertions.assertNotNull(out);
        Assertions.assertEquals("Hello World", out.getMessage().getBody());
        Assertions.assertEquals(16, out.getMessage().getHeaders().size());
        Assertions.assertEquals(
                "cheese", out.getMessage().getHeaders().get(JmsConstants.JMS_DESTINATION_NAME_PRODUCED));
        Assertions.assertNull(out.getMessage().getHeaders().get(JmsConstants.JMS_DELIVERY_MODE));
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:cheese").setBody().simple("Hello ${body}");
            }
        };
    }
}
