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
package org.apache.camel.dataformat.bindy.csv2;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.csv.BindyCsvDataFormat;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 *
 */
public class BindyMarshalUnmarshalssueTest extends CamelTestSupport {

    @Test
    public void testMarshalUnmarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        WeatherModel body = new WeatherModel();
        body.setId(123);
        body.setPlace("Central California");
        body.setDate("Wednesday November 9 2011");
        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();

        WeatherModel model = mock.getReceivedExchanges().get(0).getIn().getBody(WeatherModel.class);
        
        assertEquals(123, model.getId());
        assertEquals("Wednesday November 9 2011", model.getDate());
        assertEquals("Central California", model.getPlace());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .marshal().bindy(BindyType.Csv, org.apache.camel.dataformat.bindy.csv2.WeatherModel.class)
                    .to("direct:middle");

                from("direct:middle")
                    .unmarshal(new BindyCsvDataFormat(org.apache.camel.dataformat.bindy.csv2.WeatherModel.class))
                    .to("mock:result");
            }
        };
    }
}
