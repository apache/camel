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
package org.apache.camel.impl.transformer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverters;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeAware;
import org.apache.camel.support.DefaultDataFormat;
import org.junit.Test;

public class TransformerContractTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testInputTypeOnly() throws Exception {
        context.getTypeConverterRegistry().addTypeConverters(new MyTypeConverters());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").inputType(A.class).to("mock:a");
            }
        });
        context.start();

        MockEndpoint mock = context.getEndpoint("mock:a", MockEndpoint.class);
        mock.setExpectedCount(1);
        Object answer = template.requestBody("direct:a", "foo");
        mock.assertIsSatisfied();
        Exchange ex = mock.getExchanges().get(0);
        assertEquals(A.class, ex.getIn().getBody().getClass());
        assertEquals(A.class, answer.getClass());
    }

    @Test
    public void testOutputTypeOnly() throws Exception {
        context.getTypeConverterRegistry().addTypeConverters(new MyTypeConverters());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").outputType(A.class).to("mock:a");
            }
        });
        context.start();

        MockEndpoint mock = context.getEndpoint("mock:a", MockEndpoint.class);
        mock.setExpectedCount(1);
        Object answer = template.requestBody("direct:a", "foo");
        mock.assertIsSatisfied();
        Exchange ex = mock.getExchanges().get(0);
        assertEquals("foo", ex.getIn().getBody());
        assertEquals(A.class, answer.getClass());
    }

    @Test
    public void testScheme() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                transformer().scheme("xml").withDataFormat(new MyDataFormatDefinition());
                from("direct:a").inputType("xml").outputType("xml").to("mock:a").to("direct:b").to("mock:a2");
                from("direct:b").inputType("java").outputType("java").to("mock:b").process(ex -> {
                    ex.getIn().setBody(new B());
                });
            }
        });
        context.start();

        MockEndpoint mocka = context.getEndpoint("mock:a", MockEndpoint.class);
        MockEndpoint mocka2 = context.getEndpoint("mock:a2", MockEndpoint.class);
        MockEndpoint mockb = context.getEndpoint("mock:b", MockEndpoint.class);
        mocka.setExpectedCount(1);
        mocka2.setExpectedCount(1);
        mockb.setExpectedCount(1);
        Exchange answer = template.send("direct:a", ex -> {
            DataTypeAware message = (DataTypeAware)ex.getIn();
            message.setBody("<foo/>", new DataType("xml"));
        });
        mocka.assertIsSatisfied();
        mocka2.assertIsSatisfied();
        mockb.assertIsSatisfied();
        Exchange exa = mocka.getExchanges().get(0);
        Exchange exa2 = mocka2.getExchanges().get(0);
        Exchange exb = mockb.getExchanges().get(0);
        assertEquals("<foo/>", exa.getIn().getBody());
        assertEquals(A.class, exb.getIn().getBody().getClass());
        assertEquals(B.class, exa2.getIn().getBody().getClass());
        assertEquals("<fooResponse/>", new String((byte[])answer.getIn().getBody()));
    }

    public static class MyTypeConverters implements TypeConverters {
        @Converter
        public A toA(String in) {
            return new A();
        }
    }

    public static class MyDataFormatDefinition extends DataFormatDefinition {

        public MyDataFormatDefinition() {
            super(new DefaultDataFormat() {
                @Override
                public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
                    assertEquals(B.class, graph.getClass());
                    PrintWriter pw = new PrintWriter(new OutputStreamWriter(stream));
                    pw.print("<fooResponse/>");
                    pw.close();
                }

                @Override
                public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
                    BufferedReader br = new BufferedReader(new InputStreamReader(stream));
                    assertEquals("<foo/>", br.readLine());
                    return new A();
                }
            });
        }
    }

    public static class A {
    }

    public static class B {
    }
}
