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
package org.apache.camel.dataformat.csv;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CsvRouteTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(CsvRouteTest.class);

    @Test
    public void testSendMessage() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        // START SNIPPET: marshalInput
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("foo", "abc");
        body.put("bar", 123);
        // END SNIPPET: marshalInput
        template.sendBody("direct:start", body);

        resultEndpoint.assertIsSatisfied();
        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : list) {
            Message in = exchange.getIn();
            String text = in.getBody(String.class);

            log.debug("Received " + text);
            assertNotNull("Should be able to convert received body to a string", text);

            // order is not guaranteed with a Map (which was passed in before)
            // so we need to check for both combinations
            assertTrue("Text body has wrong value.", "abc,123".equals(text.trim())
                    || "123,abc".equals(text.trim()));
        }
    }

    @Test
    public void testMultipleMessages() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:resultMulti",
                MockEndpoint.class);
        resultEndpoint.expectedMessageCount(2);
        Map<String, Object> body1 = new LinkedHashMap<>();
        body1.put("foo", "abc");
        body1.put("bar", 123);

        Map<String, Object> body2 = new LinkedHashMap<>();
        body2.put("foo", "def");
        body2.put("bar", 456);
        body2.put("baz", 789);

        template.sendBody("direct:startMulti", body1);
        template.sendBody("direct:startMulti", body2);

        resultEndpoint.assertIsSatisfied();
        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        Message in1 = list.get(0).getIn();
        String text1 = in1.getBody(String.class);

        log.debug("Received " + text1);
        assertTrue("First CSV body has wrong value",
                Pattern.matches("(abc,123)|(123,abc)", text1.trim()));

        Message in2 = list.get(1).getIn();
        String text2 = in2.getBody(String.class);

        log.debug("Received " + text2);

        // fields should keep the same order from one call to the other
        if (text1.trim().equals("abc,123")) {
            assertEquals("Second CSV body has wrong value",
                    "def,456,789", text2.trim());
        } else {
            assertEquals("Second CSV body has wrong value",
                    "456,def,789", text2.trim());
        }
    }

    @Test
    public void testPresetConfig() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:resultMultiCustom",
                MockEndpoint.class);
        resultEndpoint.expectedMessageCount(2);
        Map<String, Object> body1 = new LinkedHashMap<>();
        body1.put("foo", "abc");
        body1.put("bar", 123);

        Map<String, Object> body2 = new LinkedHashMap<>();
        body2.put("foo", "def");
        body2.put("bar", 456);
        body2.put("baz", 789);
        body2.put("buz", "000");

        template.sendBody("direct:startMultiCustom", body1);
        template.sendBody("direct:startMultiCustom", body2);

        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        Message in1 = list.get(0).getIn();
        String text1 = in1.getBody(String.class);

        log.debug("Received " + text1);
        assertEquals("First CSV body has wrong value",
                "abc;;123", text1.trim());

        Message in2 = list.get(1).getIn();
        String text2 = in2.getBody(String.class);

        log.debug("Received " + text2);
        assertEquals("Second CSV body has wrong value",
                "def;789;456", text2.trim());

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUnMarshal() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:daltons");
        endpoint.expectedMessageCount(1);
        endpoint.assertIsSatisfied();
        Exchange exchange = endpoint.getExchanges().get(0);
        // START SNIPPET : unmarshalResult
        List<List<String>> data = (List<List<String>>) exchange.getIn().getBody();
        for (List<String> line : data) {
            LOG.debug(String.format("%s has an IQ of %s and is currently %s",
                    line.get(0), line.get(1), line.get(2)));
        }
        // END SNIPPET : unmarshalResult
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: marshalRoute
                from("direct:start").
                        marshal().csv().
                        to("mock:result");
                // END SNIPPET: marshalRoute

                from("direct:startMulti").
                        marshal().csv().
                        to("mock:resultMulti");

                CsvDataFormat customCsv = new CsvDataFormat()
                        .setDelimiter(';')
                        .setHeader(new String[]{"foo", "baz", "bar"})
                        .setSkipHeaderRecord(true);

                from("direct:startMultiCustom").
                        marshal(customCsv).
                        to("mock:resultMultiCustom");

                // START SNIPPET: unmarshalRoute
                from("file:src/test/resources/?fileName=daltons.csv&noop=true").
                        unmarshal().csv().
                        to("mock:daltons");
                // END SNIPPET: unmarshalRoute
            }
        };
    }
}
