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
package org.apache.camel.component.yql;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.yql.YqlProducer.CAMEL_YQL_HTTP_REQUEST;
import static org.apache.camel.component.yql.YqlProducer.CAMEL_YQL_HTTP_STATUS;
import static org.hamcrest.CoreMatchers.containsString;

public class YqlComponentIntegrationTest extends CamelTestSupport {

    private static final String QUERY = "select * from yahoo.finance.quotes where symbol in ('GOOG')";
    private static final String CALLBACK = "yqlCallback";

    @Produce(uri = "direct:start")
    private ProducerTemplate template;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint end;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("yql://" + QUERY + "?format=json&callback=" + CALLBACK)
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testFinanceQuote() {
        template.sendBody("");

        final Exchange exchange = end.getReceivedExchanges().get(0);
        final String body = exchange.getIn().getBody(String.class);
        final Integer status = exchange.getIn().getHeader(CAMEL_YQL_HTTP_STATUS, Integer.class);
        final String httpRequest = exchange.getIn().getHeader(CAMEL_YQL_HTTP_REQUEST, String.class);
        assertNotNull(body);
        assertThat(body, containsString(CALLBACK + "("));
        assertNotNull(status);
        assertEquals("http://query.yahooapis.com/v1/public/yql?format=json&diagnostics=false&env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys&callback=yqlCallback&q=select+*+from+"
                + "yahoo.finance.quotes+where+symbol+in+%28%27GOOG%27%29", httpRequest);
    }
}
