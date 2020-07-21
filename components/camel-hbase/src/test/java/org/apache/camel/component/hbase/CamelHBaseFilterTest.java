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
package org.apache.camel.component.hbase;

import java.util.LinkedList;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hbase.filters.ModelAwareColumnMatchingFilter;
import org.apache.hadoop.hbase.filter.Filter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CamelHBaseFilterTest extends CamelHBaseTestSupport {

    @BindToRegistry("myFilters")
    public List<Filter> addFilters() {
        List<Filter> filters = new LinkedList<>();
        filters.add(new ModelAwareColumnMatchingFilter().getFilteredList()); //not used, filters need to be rethink
        return filters;
    }

    @Test
    public void testPutMultiRowsAndScanWithFilters() throws Exception {
        putMultipleRows();
        ProducerTemplate template = context.createProducerTemplate();
        Endpoint endpoint = context.getEndpoint("direct:scan");

        Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
        exchange.getIn().setHeader(HBaseAttribute.HBASE_FAMILY.asHeader(), family[0]);
        exchange.getIn().setHeader(HBaseAttribute.HBASE_QUALIFIER.asHeader(), column[0][0]);
        exchange.getIn().setHeader(HBaseAttribute.HBASE_VALUE.asHeader(), body[0][0][0]);
        Exchange resp = template.send(endpoint, exchange);
        Message out = resp.getMessage();
        assertTrue(out.getHeaders().containsValue(body[0][0][0])
                        && out.getHeaders().containsValue(body[1][0][0])
                        && !out.getHeaders().containsValue(body[2][0][0]),
                "two first keys returned");
    }

    /**
     * Factory method which derived classes can use to create a {@link org.apache.camel.builder.RouteBuilder}
     * to define the routes for testing
     */
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("hbase://" + PERSON_TABLE);
                from("direct:scan")
                        .to("hbase://" + PERSON_TABLE + "?operation=" + HBaseConstants.SCAN + "&maxResults=2");
            }
        };
    }
}
