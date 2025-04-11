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
package org.apache.camel.processor.converter;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.converter.custom.MyBean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConvertBodyAllowNullTest extends ContextTestSupport {

    @Test
    public void testConvertMyBean() throws Exception {
        MyBean custom = context.getTypeConverter().convertTo(MyBean.class, "1:2");
        Assertions.assertNotNull(custom);

        custom = context.getTypeConverter().convertTo(MyBean.class, "");
        Assertions.assertNull(custom);
    }

    @Test
    public void testCustomConvertToAllowNullOptional() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.message(0).body().isInstanceOf(MyBean.class);

        template.sendBody("direct:custom-optional", "1:2");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCustomConvertToAllowNull() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.message(0).body().isNull();

        template.sendBody("direct:custom-mandatory", "");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testConvertAllowNull() throws Exception {
        Object val = context.getTypeConverter().convertTo(Integer.class, Float.NaN);
        Assertions.assertNull(val);
        val = context.getTypeConverter().mandatoryConvertTo(Integer.class, Float.NaN);
        Assertions.assertNull(val);
        val = context.getTypeConverter().tryConvertTo(Integer.class, Float.NaN);
        Assertions.assertNull(val);

        val = context.getTypeConverter().convertTo(Integer.class, 123);
        Assertions.assertEquals(123, val);
        val = context.getTypeConverter().mandatoryConvertTo(Integer.class, 123);
        Assertions.assertEquals(123, val);
        val = context.getTypeConverter().tryConvertTo(Integer.class, 123);
        Assertions.assertEquals(123, val);
    }

    @Test
    public void testConvertAllowNullWithExchange() throws Exception {
        Exchange exchange = context.getEndpoint("mock:result").createExchange();

        Object val = context.getTypeConverter().convertTo(Integer.class, exchange, Float.NaN);
        Assertions.assertNull(val);
        val = context.getTypeConverter().mandatoryConvertTo(Integer.class, exchange, Float.NaN);
        Assertions.assertNull(val);
        val = context.getTypeConverter().tryConvertTo(Integer.class, exchange, Float.NaN);
        Assertions.assertNull(val);

        val = context.getTypeConverter().convertTo(Integer.class, exchange, 123);
        Assertions.assertEquals(123, val);
        val = context.getTypeConverter().mandatoryConvertTo(Integer.class, exchange, 123);
        Assertions.assertEquals(123, val);
        val = context.getTypeConverter().tryConvertTo(Integer.class, exchange, 123);
        Assertions.assertEquals(123, val);
    }

    @Test
    public void testConvertToAllowNullOptional() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.message(0).body().isNull();

        template.sendBody("direct:optional", Float.NaN);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testConvertToAllowNull() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.message(0).body().isNull();

        template.sendBody("direct:mandatory", Float.NaN);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHeaderConvertToAllowNullOptional() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.message(0).header("foo").isNull();

        template.sendBodyAndHeader("direct:header-optional", "Hello World", "foo", Float.NaN);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHeaderConvertToAllowNull() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.message(0).header("foo").isNull();

        template.sendBodyAndHeader("direct:header-mandatory", "Hello World", "foo", Float.NaN);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testVarConvertToAllowNullOptional() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.message(0).variable("foo").isNull();

        fluentTemplate.withVariable("foo", Float.NaN).withBody("Hello World").to("direct:var-optional").send();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testVarConvertToAllowNull() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.message(0).variable("foo").isNull();

        fluentTemplate.withVariable("foo", Float.NaN).withBody("Hello World").to("direct:var-mandatory").send();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                context.setStreamCaching(false);

                from("direct:optional").convertBodyTo(Integer.class, false).to("mock:result");
                from("direct:mandatory").convertBodyTo(Integer.class).to("mock:result");

                from("direct:header-optional").convertHeaderTo("foo", Integer.class, false).to("mock:result");
                from("direct:header-mandatory").convertHeaderTo("foo", Integer.class).to("mock:result");

                from("direct:var-optional").convertVariableTo("foo", Integer.class, false).to("mock:result");
                from("direct:var-mandatory").convertVariableTo("foo", Integer.class).to("mock:result");

                from("direct:custom-optional").convertBodyTo(MyBean.class, false).to("mock:result");
                from("direct:custom-mandatory").convertBodyTo(MyBean.class).to("mock:result");
            }
        };
    }

}
