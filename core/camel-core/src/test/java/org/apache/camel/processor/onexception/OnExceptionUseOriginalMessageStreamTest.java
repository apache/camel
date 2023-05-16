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
package org.apache.camel.processor.onexception;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OnExceptionUseOriginalMessageStreamTest extends ContextTestSupport {

    @Test
    void convertBodyWithStreamCache() {
        // Cached stream is closed by explicit type converter
        String data = "data";
        InputStream is = new ByteArrayInputStream(data.getBytes());
        Object out = template.requestBody("direct:convertBodyWithStreamCache", is, Object.class);
        Assertions.assertEquals(data, out);
    }

    @Test
    void convertBodyWithoutStreamCache() {
        // Uncached stream is closed by reading with type converter
        String data = "data";
        InputStream is = new ByteArrayInputStream(data.getBytes());
        Object out = template.requestBody("direct:convertBodyWithoutStreamCache", is, Object.class);
        Assertions.assertEquals(data, out);
    }

    @Test
    void unmarshallWithStreamCache() {
        // Cached stream is closed unmarshalling
        String data = "{\"test\": \"data\"";
        InputStream is = new ByteArrayInputStream(data.getBytes());
        Object out = template.requestBody("direct:unmarshallWithStreamCache", is, Object.class);
        Assertions.assertEquals(data, out);
    }

    @Test
    void unmarshallWithoutStreamCache() {
        // Uncached stream is closed by reading with unmarshaller
        String data = "{\"test\": \"data\"";
        InputStream is = new ByteArrayInputStream(data.getBytes());
        Object out = template.requestBody("direct:unmarshallWithoutStreamCache", is, Object.class);
        Assertions.assertEquals(data, out);
    }

    @Test
    void unmarshallInvalidWithoutStreamCache() {
        // Uncached stream is closed by reading with unmarshaller
        String data = "{\"test\": \"data\"";
        InputStream is = new ByteArrayInputStream(data.getBytes());
        Object out = template.requestBody("direct:convertBodyInvalidUnmarshallWithoutStreamCache", is, Object.class);
        Assertions.assertEquals(data, out);
    }

    @Test
    void noStreamReading() {
        // Both cached and uncached streams are available because if it is not used
        String data = "data";
        InputStream is = new ByteArrayInputStream(data.getBytes());
        Object out = template.requestBody("direct:noStreamReading", is, Object.class);
        Assertions.assertEquals(data, out);
    }

    @Test
    void setBodyAsExchangeProperty() {
        // Data is converted to string and put in exchange property
        String data = "data";
        InputStream is = new ByteArrayInputStream(data.getBytes());
        Object out = template.requestBody("direct:setBodyAsExchangeProperty", is, Object.class);
        Assertions.assertEquals(data, out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(ExceptionOne.class, MyDataFormatException.class)
                        .useOriginalMessage()
                        .convertBodyTo(String.class)
                        .handled(true);

                onException(ExceptionTwo.class)
                        .setBody(exchangeProperty("OriginalBody"))
                        .convertBodyTo(String.class)
                        .handled(true);

                from("direct:convertBodyWithStreamCache").streamCaching()
                        .convertBodyTo(String.class)
                        .throwException(new ExceptionOne());

                from("direct:convertBodyWithoutStreamCache").noStreamCaching()
                        .convertBodyTo(String.class)
                        .throwException(new ExceptionOne());

                from("direct:unmarshallWithStreamCache").streamCaching()
                        .unmarshal(new MyDataFormat())
                        .throwException(new ExceptionOne());

                from("direct:unmarshallWithoutStreamCache").noStreamCaching()
                        .unmarshal(new MyDataFormat())
                        .throwException(new ExceptionOne());

                from("direct:convertBodyInvalidUnmarshallWithoutStreamCache").noStreamCaching()
                        .convertBodyTo(String.class)
                        .unmarshal(new MyDataFormat());

                from("direct:noStreamReading").streamCaching()
                        .throwException(new ExceptionOne());

                from("direct:setBodyAsExchangeProperty").noStreamCaching()
                        .convertBodyTo(String.class)
                        .setProperty("OriginalBody", body())
                        .throwException(new ExceptionTwo());
            }
        };
    }

    public static class ExceptionOne extends Exception {

    }

    public static class ExceptionTwo extends Exception {

    }

    public static class MyDataFormatException extends Exception {

        public MyDataFormatException(String message) {
            super(message);
        }
    }

    public class MyDataFormat extends ServiceSupport implements DataFormat {

        @Override
        public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
            // noop
        }

        @Override
        public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
            // simulate reading the entire stream so its not re-readable later
            String s = IOConverter.toString(stream, exchange);
            throw new MyDataFormatException(s);
        }
    }
}
