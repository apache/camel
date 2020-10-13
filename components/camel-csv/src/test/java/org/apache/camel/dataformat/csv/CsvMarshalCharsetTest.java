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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CsvMarshalCharsetTest extends CamelTestSupport {

    @Test
    void testMarshal() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:daltons");
        endpoint.expectedMessageCount(1);

        List<List<String>> data = new ArrayList<>();
        data.add(0, new ArrayList<String>());
        data.get(0).add(0, "L\u00fccky Luke");
        Exchange in = createExchangeWithBody(data);
        in.setProperty(Exchange.CHARSET_NAME, "ISO-8859-1");
        template.send("direct:start", in);

        endpoint.assertIsSatisfied();

        Exchange exchange = endpoint.getExchanges().get(0);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
        assertTrue(body.startsWith("L\u00fccky Luke"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").marshal().csv().to("mock:daltons");
            }
        };
    }
}
