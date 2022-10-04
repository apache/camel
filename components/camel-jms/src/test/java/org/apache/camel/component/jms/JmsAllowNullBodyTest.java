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

import javax.jms.JMSException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 *
 */
public class JmsAllowNullBodyTest extends AbstractJMSTest {

    @Test
    public void testAllowNullBodyDefault() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).body().isNull();
        getMockEndpoint("mock:result").message(0).header("bar").isEqualTo(123);

        // allow null body is default enabled
        template.sendBodyAndHeader("activemq:queue:JmsAllowNullBodyTest", null, "bar", 123);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testAllowNullBody() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).body().isNull();
        getMockEndpoint("mock:result").message(0).header("bar").isEqualTo(123);

        template.sendBodyAndHeader("activemq:queue:JmsAllowNullBodyTest?allowNullBody=true", null, "bar", 123);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testAllowNullTextBody() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).body().isNull();
        getMockEndpoint("mock:result").message(0).header("bar").isEqualTo(123);

        template.sendBodyAndHeader("activemq:queue:JmsAllowNullBodyTest?allowNullBody=true&jmsMessageType=Text", null, "bar",
                123);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testNoAllowNullBody() {
        try {
            template.sendBodyAndHeader("activemq:queue:JmsAllowNullBodyTest?allowNullBody=false", null, "bar", 123);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            JMSException cause = assertIsInstanceOf(JMSException.class, e.getCause().getCause());
            assertEquals("Cannot send message as message body is null, and option allowNullBody is false.", cause.getMessage());
        }
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsAllowNullBodyTest").to("mock:result");
            }
        };
    }
}
