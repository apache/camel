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
package org.apache.camel.component.flatpack;

import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@CamelSpringTest
@ContextConfiguration
public class DelimitedAllowLongTest {
    private static final Logger LOG = LoggerFactory.getLogger(DelimitedAllowLongTest.class);

    @EndpointInject("mock:results")
    protected MockEndpoint results;

    @EndpointInject("mock:results-df")
    protected MockEndpoint resultsdf;

    @EndpointInject("mock:results-xml")
    protected MockEndpoint resultsxml;

    protected String[] expectedItemDescriptions = { "SOME VALVE", "AN ENGINE", "A BELT", "A BOLT" };

    @Test
    public void testCamel() throws Exception {
        results.expectedMessageCount(4);
        results.assertIsSatisfied();

        int counter = 0;
        List<Exchange> list = results.getReceivedExchanges();
        for (Exchange exchange : list) {
            Message in = exchange.getIn();
            assertEquals(in.getHeader("camelFlatpackCounter"), counter, "counter");
            Map<?, ?> body = in.getBody(Map.class);
            assertNotNull(body, "Should have found body as a Map but was: " + ObjectHelper.className(in.getBody()));
            assertEquals(expectedItemDescriptions[counter], body.get("ITEM_DESC"), "ITEM_DESC");
            LOG.info("Result: " + counter + " = " + body);
            counter++;
        }
    }

    @Test
    public void testFlatpackDataFormat() throws Exception {
        resultsdf.expectedMessageCount(1);
        resultsdf.assertIsSatisfied();

        Exchange exchange = resultsdf.getReceivedExchanges().get(0);
        DataSetList data = exchange.getIn().getBody(DataSetList.class);
        int counter = 0;
        for (Map<String, Object> map : data) {
            assertEquals(expectedItemDescriptions[counter], map.get("ITEM_DESC"), "ITEM_DESC");
            counter++;
        }
    }

    @Test
    public void testFlatpackDataFormatXML() throws Exception {
        resultsxml.expectedMessageCount(1);
        resultsxml.assertIsSatisfied();

        Exchange exchange = resultsxml.getReceivedExchanges().get(0);
        DataSetList data = exchange.getIn().getBody(DataSetList.class);
        int counter = 0;
        for (Map<String, Object> map : data) {
            assertEquals(expectedItemDescriptions[counter], map.get("ITEM_DESC"), "ITEM_DESC");
            counter++;
        }
    }
}
