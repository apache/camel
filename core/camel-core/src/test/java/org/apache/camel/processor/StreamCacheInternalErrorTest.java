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
package org.apache.camel.processor;

import java.io.ByteArrayInputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.stream.ByteArrayInputStreamCache;
import org.apache.camel.support.TypeConverterSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StreamCacheInternalErrorTest extends ContextTestSupport {

    private BodyToStreamCacheConverter converter = new BodyToStreamCacheConverter();

    @Test
    public void testOk() throws Exception {
        converter.reset();

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:a").message(0).body().isInstanceOf(StreamCache.class);
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").message(0).body().isInstanceOf(StreamCache.class);
        getMockEndpoint("mock:c").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:c").message(0).body().isInstanceOf(StreamCache.class);
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").message(0).body().isInstanceOf(StreamCache.class);
        getMockEndpoint("mock:exception").expectedMessageCount(0);

        template.sendBody("direct:start", new MyBody("Hello World"));

        assertMockEndpointsSatisfied();

        Assertions.assertEquals(1, converter.getInvoked());
    }

    @Test
    public void testError() throws Exception {
        converter.reset();

        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:error").message(0).body().isInstanceOf(MyBody.class);
        getMockEndpoint("mock:exception").message(0).body().contains(
                "Handled big error due to Error during type conversion from type: org.apache.camel.processor.StreamCacheInternalErrorTest.MyBody");
        getMockEndpoint("mock:exception").message(0).body().contains("Kaboom");

        template.sendBody("direct:start", new MyBody("Kaboom"));

        assertMockEndpointsSatisfied();

        Assertions.assertEquals(1, converter.getInvoked());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                context.getTypeConverterRegistry().addTypeConverter(StreamCache.class, MyBody.class, converter);

                // enable support for stream caching
                context.setStreamCaching(true);

                onException(Exception.class)
                        .maximumRedeliveries(2)
                        .handled(true)
                        .to("mock:error")
                        .setBody(simple("Handled big error due to ${exception.message}"))
                        .to("mock:exception");

                from("direct:start").tracing()
                        .to("mock:a")
                        .to("mock:b")
                        .to("mock:c")
                        .to("mock:result");
            }
        };
    }

    private static class BodyToStreamCacheConverter extends TypeConverterSupport {

        private int invoked;

        @Override
        public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
            return tryConvertTo(type, exchange, value);
        }

        @Override
        public <T> T tryConvertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
            invoked++;

            String str = value.toString();
            if ("Kaboom".equals(str)) {
                throw new IllegalArgumentException("Kaboom");
            } else {
                byte[] data = str.getBytes();
                return (T) new ByteArrayInputStreamCache(new ByteArrayInputStream(data));
            }
        }

        public int getInvoked() {
            return invoked;
        }

        public void reset() {
            invoked = 0;
        }
    }

    private static class MyBody {
        private String body;

        public MyBody(String body) {
            this.body = body;
        }

        @Override
        public String toString() {
            return body;
        }
    }
}
