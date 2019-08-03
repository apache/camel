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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CsvRouteCharsetTest extends CamelTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void testUnMarshal() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:daltons");
        endpoint.expectedMessageCount(1);
        endpoint.assertIsSatisfied();

        Exchange exchange = endpoint.getExchanges().get(0);
        List<List<String>> data = (List<List<String>>) exchange.getIn().getBody();
        assertEquals("Jäck Dalton", data.get(0).get(0));
        assertEquals("Jöe Dalton", data.get(1).get(0));
        assertEquals("Lücky Luke", data.get(2).get(0));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("file:src/test/resources/?fileName=daltons-utf-8.csv&noop=true").
                        unmarshal().csv().
                        to("mock:daltons");
            }
        };
    }
}
