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
package org.apache.camel.dataformat.bindy.fixed.multibytes;

import java.nio.charset.StandardCharsets;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
import org.apache.camel.dataformat.bindy.fixed.BindyFixedLengthDataFormat;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class BindyMultiBytesTest extends CamelTestSupport {

    @Produce("direct:in")
    private ProducerTemplate producer;

    @EndpointInject("mock:result")
    private MockEndpoint result;


    // *************************************************************************
    // TESTS
    // *************************************************************************

    /**
     * Let's assume we want to read the content of a 10 bytes record from an UTF-8 encoded file.
     * test string takes 10 bytes with 9 characters (2 char = 3 bytes content + padding).
     * I assume to be able to read 9 char string from this 10 bytes fixed length record with bindy.
     */
    @Test
    public void testMultiBytes() throws Exception {
        String test = "a\u00DF        ";
        assertEquals("Should be 10 length", 10, test.length());

        byte[] testAsBytes = test.getBytes(StandardCharsets.UTF_8);
        assertEquals("A\u00DF takes 11 bytes, because \u00DF takes 2", 11, testAsBytes.length);

        result.expectedMessagesMatches(exchange -> test.equals(exchange.getIn().getBody(TestRecord.class).getField1()));

        producer.sendBody(test);

        result.assertIsSatisfied();
    }

    // *************************************************************************
    // ROUTES
    // *************************************************************************

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        RouteBuilder routeBuilder = new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:in")
                        .setHeader(Exchange.CHARSET_NAME, constant(StandardCharsets.UTF_8.name()))
                        .unmarshal(new BindyFixedLengthDataFormat(TestRecord.class))
                        .to("mock:result");
            }
        };

        return routeBuilder;
    }

    @FixedLengthRecord(length = 10, paddingChar = ' ')
    public static class TestRecord {

        @DataField(pos = 1, length = 10)
        private String field1;

        public String getField1() {
            return field1;
        }

        public void setField1(String field1) {
            this.field1 = field1;
        }

    }
}