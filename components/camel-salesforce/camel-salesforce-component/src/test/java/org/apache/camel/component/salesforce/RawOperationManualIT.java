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
package org.apache.camel.component.salesforce;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RawOperationManualIT extends AbstractSalesforceTestBase {

    @Test
    public void testCreate() {
        String body = "{\n" +
                      "    \"LastName\" : \"TestLast\"\n" +
                      "}";

        Exchange exchange = fluentTemplate.withBody(body)
                .to("salesforce:raw?rawMethod=POST&rawPath=/services/data/v" + SalesforceEndpointConfig.DEFAULT_VERSION
                    + "/sobjects/Contact")
                .send();

        String response = exchange.getIn().getBody(String.class);
        assertNull(exchange.getException());
        assertTrue(response.contains("success"));
    }

    @Test
    public void testCreateXml() {
        String body = "<Contact>\n" +
                      "    <LastName>TestLast</LastName>\n" +
                      "</Contact>";

        Exchange exchange = fluentTemplate.withBody(body)
                .to("salesforce:raw?format=XML&rawMethod=POST&rawPath=/services/data/v"
                    + SalesforceEndpointConfig.DEFAULT_VERSION + "/sobjects/Contact")
                .send();

        String response = exchange.getIn().getBody(String.class);
        assertNull(exchange.getException());
        assertTrue(response.contains("success"));
    }

    @Test
    public void testQuery() {

        Exchange exchange = fluentTemplate
                .withHeader("q", "SELECT Id FROM Contact LIMIT 10")
                .to("salesforce:raw?format=JSON&rawMethod=GET&rawQueryParameters=q&rawPath=/services/data/v"
                    + SalesforceEndpointConfig.DEFAULT_VERSION + "/query")
                .send();

        String response = exchange.getIn().getBody(String.class);
        assertTrue(response.contains("done"));
        assertTrue(response.contains("totalSize"));
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
            }
        };
    }
}
