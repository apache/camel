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
package org.apache.camel.component.flatpack;

import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
/**
 * @version
 */
@ContextConfiguration
public class FixedLengthAllowLongTest extends AbstractJUnit4SpringContextTests {
    private static final Logger LOG = LoggerFactory.getLogger(FixedLengthAllowLongTest.class);

    @EndpointInject(uri = "mock:results")
    protected MockEndpoint results;

    @EndpointInject(uri = "mock:results-df")
    protected MockEndpoint resultsdf;

    @EndpointInject(uri = "mock:results-xml")
    protected MockEndpoint resultsxml;

    protected String[] expectedFirstName = {"JOHN-LONG", "JIMMY-LONG", "JANE-LONG", "FRED-LONG"};

    @Test
    public void testCamel() throws Exception {
        results.expectedMessageCount(4);
        results.assertIsSatisfied();

        int counter = 0;
        List<Exchange> list = results.getReceivedExchanges();
        for (Exchange exchange : list) {
            Message in = exchange.getIn();
            assertEquals("counter", in.getHeader("camelFlatpackCounter"), counter);
            Map<?, ?> body = in.getBody(Map.class);
            assertNotNull("Should have found body as a Map but was: " + ObjectHelper.className(in.getBody()), body);
            assertEquals("FIRSTNAME", expectedFirstName[counter], body.get("FIRSTNAME"));
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
            assertEquals("FIRSTNAME", expectedFirstName[counter], map.get("FIRSTNAME"));
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
            assertEquals("FIRSTNAME", expectedFirstName[counter], map.get("FIRSTNAME"));
            counter++;
        }
    }
}
