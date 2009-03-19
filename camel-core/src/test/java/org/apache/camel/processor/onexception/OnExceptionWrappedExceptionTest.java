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
package org.apache.camel.processor.onexception;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.RouteBuilder;
import static org.apache.camel.util.ObjectHelper.wrapRuntimeCamelException;

/**
 * Unit test to test that onException handles wrapped exceptions
 */
public class OnExceptionWrappedExceptionTest extends ContextTestSupport {

    public void testWrappedException() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:wrapped").expectedMessageCount(1);
        getMockEndpoint("mock:end").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getTypeConverterRegistry().addTypeConverter(LocalDateTime.class, String.class, new MyLocalDateTimeConverter());

                errorHandler(deadLetterChannel("mock:error"));

                onException(IllegalArgumentException.class).handled(true).to("mock:wrapped");

                from("direct:start").convertBodyTo(LocalDateTime.class).to("mock:end");
            }
        };
    }

    public static class LocalDateTime {
    }

    private class MyLocalDateTimeConverter implements TypeConverter {

        public <T> T convertTo(Class<T> type, Object value) {
            // simulate @Converter where we wrap thrown exception in RuntimeCamelException
            throw wrapRuntimeCamelException(new IllegalArgumentException("Bad Data"));
        }

        public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
            return convertTo(type, value);
        }

        public <T> T mandatoryConvertTo(Class<T> type, Object value) {
            return convertTo(type, value);
        }

        public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value) {
            return convertTo(type, value);
        }
    }

}