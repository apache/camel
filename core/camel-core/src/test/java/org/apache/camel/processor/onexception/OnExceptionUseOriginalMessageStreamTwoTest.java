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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.StreamCache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OnExceptionUseOriginalMessageStreamTwoTest extends ContextTestSupport {

    private final List<String> list1 = new ArrayList<>();
    private final List<String> list2 = new ArrayList<>();

    @Test
    void convertUseOriginalMessage() {
        String data = "data";
        InputStream is = new ByteArrayInputStream(data.getBytes());
        template.sendBody("direct:start", is);

        Assertions.assertEquals(list1.get(0), list2.get(0));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class)
                        .useOriginalMessage()
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                Assertions.assertTrue(exchange.getMessage().getBody() instanceof StreamCache);
                                String s = exchange.getMessage().getBody(String.class);
                                list1.add(s);
                            }
                        })
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                Assertions.assertTrue(exchange.getMessage().getBody() instanceof StreamCache);
                                String s = exchange.getMessage().getBody(String.class);
                                list2.add(s);
                            }
                        })
                        .handled(true);

                from("direct:start")
                        .unmarshal(new MyDataFormat());
            }
        };
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
