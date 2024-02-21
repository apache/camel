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

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class JmsValidatorTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;

    @Test
    void testJmsValidator() throws Exception {
        getMockEndpoint("mock:valid").expectedMessageCount(1);
        getMockEndpoint("mock:invalid").expectedMessageCount(0);
        getMockEndpoint("mock:finally").expectedMessageCount(1);

        String body = "<?xml version=\"1.0\"?>\n<p>Hello world!</p>";
        template.sendBody("jms:queue:inbox", body);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testJmsValidatorInvalid() throws Exception {
        getMockEndpoint("mock:valid").expectedMessageCount(0);
        getMockEndpoint("mock:invalid").expectedMessageCount(1);
        getMockEndpoint("mock:finally").expectedMessageCount(1);

        String body = "<?xml version=\"1.0\"?>\n<foo>Kaboom</foo>";
        template.sendBody("jms:queue:inbox", body);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:inbox")
                        .convertBodyTo(String.class)
                        .doTry()
                        .to("validator:file:src/test/resources/org/apache/camel/component/jms/JmsValidatorTestSchema.xsd")
                        .to("jms:queue:valid")
                        .doCatch(ValidationException.class)
                        .to("jms:queue:invalid")
                        .doFinally()
                        .to("jms:queue:finally")
                        .end();

                from("jms:queue:valid").to("mock:valid");
                from("jms:queue:invalid").to("mock:invalid");
                from("jms:queue:finally").to("mock:finally");
            }
        };
    }

    @Override
    protected String getComponentName() {
        return "jms";
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

}
