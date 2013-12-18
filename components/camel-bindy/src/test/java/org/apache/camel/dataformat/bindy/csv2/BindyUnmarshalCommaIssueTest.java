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
package org.apache.camel.dataformat.bindy.csv2;

import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 */
public class BindyUnmarshalCommaIssueTest extends CamelTestSupport {

    @Test
    public void testBindyUnmarshalNoCommaIssue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        String body = "123,\"Wednesday November 9 2011\",\"Central California\"";
        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();
     
        WeatherModel model = mock.getReceivedExchanges().get(0).getIn().getBody(WeatherModel.class);
        
        assertEquals(123, model.getId());
        assertEquals("Wednesday November 9 2011", model.getDate());
        assertEquals("Central California", model.getPlace());
    }

    @Test
    public void testBindyUnmarshalCommaIssue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        String body = "123,\"Wednesday, November 9, 2011\",\"Central California\"";
        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();

        WeatherModel model = mock.getReceivedExchanges().get(0).getIn().getBody(WeatherModel.class);

        assertEquals(123, model.getId());
        assertEquals("Wednesday, November 9, 2011", model.getDate());
        assertEquals("Central California", model.getPlace());
    }

    @Test
    public void testBindyUnmarshalCommaIssueTwo() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        String body = "123,\"Wednesday, November 9, 2011\",\"Central California, United States\"";
        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();

        WeatherModel model = mock.getReceivedExchanges().get(0).getIn().getBody(WeatherModel.class);
        
        assertEquals(123, model.getId());
        assertEquals("Wednesday, November 9, 2011", model.getDate());
        assertEquals("Central California, United States", model.getPlace());
    }

    @Test
    @Ignore("To fix CAMEL-5871. doesn't support the signle quote test case any more")
    public void testBindyUnmarshalSingleQuoteCommaIssueTwo() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        String body = "123,'Wednesday, November 9, 2011','Central California, United States'";
        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();

        Map<?, ?> map = (Map<?, ?>) mock.getReceivedExchanges().get(0).getIn().getBody(List.class).get(0);
        WeatherModel model = (WeatherModel) map.values().iterator().next();

        assertEquals(123, model.getId());
        assertEquals("Wednesday, November 9, 2011", model.getDate());
        assertEquals("Central California, United States", model.getPlace());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .unmarshal().bindy(BindyType.Csv, org.apache.camel.dataformat.bindy.csv2.WeatherModel.class)
                    .to("mock:result");
            }
        };
    }
}
