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
package org.apache.camel.dataformat.bindy.csv;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.model.simple.pipeline.MyData;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version
 */
public class BindyPipeDelimiterTest extends CamelTestSupport {

    @Test
    public void testBindyPipeDelimiterUnmarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:unmarshal", "COL1|COL2|COL3\nHAPPY | NEW | YEAR");

        assertMockEndpointsSatisfied();

        MyData rec1 = (MyData) mock.getReceivedExchanges().get(0).getIn().getBody(List.class).get(0);
        MyData rec2 = (MyData) mock.getReceivedExchanges().get(0).getIn().getBody(List.class).get(1);

        //MyData rec1 = (MyData) map1.values().iterator().next();
        //MyData rec2 = (MyData) map2.values().iterator().next();

        assertEquals("COL1", rec1.getCol1());
        assertEquals("COL2", rec1.getCol2());
        assertEquals("COL3", rec1.getCol3());

        assertEquals("HAPPY ", rec2.getCol1());
        assertEquals(" NEW ", rec2.getCol2());
        assertEquals(" YEAR", rec2.getCol3());
    }

    @Test
    public void testBindyPipeDelimiterMarshalShouldHaveCorrectHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().convertToString().startsWith("col1|col2|col3");

        MyData data = new MyData();
        data.setCol1("HAPPY");
        data.setCol2("NEW");
        data.setCol3("YEAR");
        template.sendBody("direct:marshal", data);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBindyPipeDelimiterMarshalShouldContainMyData() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().convertToString().contains("HAPPY|NEW|YEAR");

        MyData data = new MyData();
        data.setCol1("HAPPY");
        data.setCol2("NEW");
        data.setCol3("YEAR");
        template.sendBody("direct:marshal", data);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:unmarshal")
                        .unmarshal().bindy(BindyType.Csv, org.apache.camel.dataformat.bindy.model.simple.pipeline.MyData.class)
                        .to("log:after.unmarshal")
                        .to("mock:result");

                from("direct:marshal")
                        .marshal().bindy(BindyType.Csv, org.apache.camel.dataformat.bindy.model.simple.pipeline.MyData.class)
                        .to("log:after.marshal")
                        .to("mock:result");
            }
        };
    }

}
