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
package org.apache.camel.dataformat.beanio.csv;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.beanio.BeanIODataFormat;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 */
public class CsvTest extends CamelTestSupport {
    private static final String FIXED_DATA =
            "James,Strachan,22" + LS + "Claus,Ibsen,21" + LS;

    private boolean verbose;

/*
    @Test
    public void testMarshal() throws Exception {
        List<Employee> employees = getEmployees();

        MockEndpoint mock = getMockEndpoint("mock:beanio-marshal");
        mock.expectedBodiesReceived(FIXED_DATA);

        template.sendBody("direct:marshal", employees);

        mock.assertIsSatisfied();
    }
*/

    @Test
    public void testUnmarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:beanio-unmarshal");
        mock.expectedMessageCount(2);

        template.sendBody("direct:unmarshal", FIXED_DATA);

        mock.assertIsSatisfied();

        List<Exchange> exchanges = mock.getExchanges();
        if (verbose) {
            for (Exchange exchange : exchanges) {
                Object body = exchange.getIn().getBody();
                log.info("received message {} of class {}", body, body.getClass().getName());
            }
        }
        List<Map> results = new ArrayList<Map>();
        for (Exchange exchange : exchanges) {
            Map body = exchange.getIn().getBody(Map.class);
            if (body != null) {
                results.add(body);
            }
        }
        assertRecord(results, 0, "James", "Strachan", 22);
        assertRecord(results, 1, "Claus", "Ibsen", 21);
    }

    protected static void assertRecord(List<Map> results, int index, String expectedFirstName, String expectedLastName, int expectedAge) {
        assertTrue("Not enough Map messages received: " + results.size(), results.size() > index);
        Map map = results.get(index);
        assertNotNull("No map result found for index " + index, map);

        String text = "bodyAsMap(" + index + ") ";
        assertEquals(text + "firstName", expectedFirstName, map.get("firstName"));
        assertEquals(text + "lastName", expectedLastName, map.get("lastName"));
        assertEquals(text + "age", expectedAge, map.get("age"));
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                // setup beanio data format using the mapping file, loaded from the classpath
                DataFormat format = new BeanIODataFormat(
                        "org/apache/camel/dataformat/beanio/csv/mappings.xml",
                        "stream1");

                // a route which uses the bean io data format to format a CSV data
                // to java objects
                from("direct:unmarshal")
                        .unmarshal(format)
                                // and then split the message body so we get a message for each row
                        .split(body())
                        .to("mock:beanio-unmarshal");

                // convert list of java objects back to flat format
                from("direct:marshal")
                        .marshal(format)
                        .to("mock:beanio-marshal");
                // END SNIPPET: e1
            }
        };
    }
}
