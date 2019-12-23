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
import java.util.HashMap;
import java.util.Map;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.TypeConverterSupport;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.component.jms.JmsConstants.JMS_MESSAGE_TYPE;

public class JmsMessageTypeTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("jms", jmsComponentAutoAcknowledge(connectionFactory));

        camelContext.getTypeConverterRegistry().addTypeConverter(byte[].class, MyFooBean.class, new MyFooBean());
        camelContext.getTypeConverterRegistry().addTypeConverter(String.class, MyFooBean.class, new MyFooBean());
        camelContext.getTypeConverterRegistry().addTypeConverter(Map.class, MyFooBean.class, new MyFooBean());

        return camelContext;
    }

    @Test
    public void testHeaderTextType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        // we use Text type then it should be a String
        mock.message(0).body().isInstanceOf(String.class);

        // we send an object and force it to use Text type
        template.sendBodyAndHeader("direct:foo", new MyFooBean("World"), JMS_MESSAGE_TYPE, "Text");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testConvertTextType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        // we use Text type then it should be a String
        mock.message(0).body().isInstanceOf(String.class);

        // we send an object and force it to use Text type
        template.sendBody("direct:text", new MyFooBean("World"));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTextType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        // we use Text type then it should be a String
        mock.message(0).body().isInstanceOf(String.class);

        // we send a string and force it to use Text type
        template.sendBody("direct:text", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHeaderBytesType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World".getBytes());
        mock.message(0).body().isInstanceOf(byte[].class);

        // we send an object and force it to use Bytes type
        template.sendBodyAndHeader("direct:foo", new MyFooBean("World"), JMS_MESSAGE_TYPE, "Bytes");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testConvertBytesType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World".getBytes());
        mock.message(0).body().isInstanceOf(byte[].class);

        // we send an object and force it to use Bytes type
        template.sendBody("direct:bytes", new MyFooBean("World"));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBytesType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World".getBytes());
        mock.message(0).body().isInstanceOf(byte[].class);

        // we send a string and force it to use Bytes type
        template.sendBody("direct:bytes", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHeaderMapType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Map.class);

        // we send an object and force it to use Map type
        template.sendBodyAndHeader("direct:foo", new MyFooBean("Claus"), JMS_MESSAGE_TYPE, "Map");

        assertMockEndpointsSatisfied();

        assertEquals("Claus", mock.getExchanges().get(0).getIn().getBody(Map.class).get("name"));
    }

    @Test
    public void testConvertMapType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Map.class);

        // we send an object and force it to use Map type
        template.sendBody("direct:map", new MyFooBean("Claus"));

        assertMockEndpointsSatisfied();

        assertEquals("Claus", mock.getExchanges().get(0).getIn().getBody(Map.class).get("name"));
    }

    @Test
    public void testMapType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Map.class);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Claus");

        // we send a Map object and force it to use Map type
        template.sendBody("direct:map", body);

        assertMockEndpointsSatisfied();

        assertEquals("Claus", mock.getExchanges().get(0).getIn().getBody(Map.class).get("name"));
    }

    @Test
    public void testHeaderObjectType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        // we use Object type then it should be a MyFooBean object
        mock.message(0).body().isInstanceOf(MyFooBean.class);

        // we send an object and force it to use Object type
        template.sendBodyAndHeader("direct:foo", new MyFooBean("James"), JMS_MESSAGE_TYPE, "Object");

        assertMockEndpointsSatisfied();

        assertEquals("James", mock.getExchanges().get(0).getIn().getBody(MyFooBean.class).getName());
    }

    @Test
    public void testObjectType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        // we use Object type then it should be a MyFooBean object
        mock.message(0).body().isInstanceOf(MyFooBean.class);

        // we send an object and force it to use Object type
        template.sendBody("direct:object", new MyFooBean("James"));

        assertMockEndpointsSatisfied();

        assertEquals("James", mock.getExchanges().get(0).getIn().getBody(MyFooBean.class).getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:text").to("jms:queue:foo?jmsMessageType=Text");
                from("direct:bytes").to("jms:queue:foo?jmsMessageType=Bytes");
                from("direct:map").to("jms:queue:foo?jmsMessageType=Map");
                from("direct:object").to("jms:queue:foo?jmsMessageType=Object");

                from("direct:foo").to("jms:queue:foo");

                from("jms:queue:foo").to("mock:result");
            }
        };
    }

    public static final class MyFooBean extends TypeConverterSupport implements Serializable {

        private static final long serialVersionUID = 1L;
        private String name;

        private MyFooBean() {
        }

        private MyFooBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
            if (type.isAssignableFrom(String.class)) {
                return (T) ("Hello " + ((MyFooBean)value).getName());
            }
            if (type.isAssignableFrom(byte[].class)) {
                return (T) ("Bye " + ((MyFooBean)value).getName()).getBytes();
            }
            if (type.isAssignableFrom(Map.class)) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", ((MyFooBean)value).getName());
                return (T) map;
            }
            return null;
        }
    }
}
