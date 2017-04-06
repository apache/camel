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

import java.math.BigDecimal;

import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.model.csv.MyCsvRecord2;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class BindySimpleCsvCAMEL11065Test extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    private MockEndpoint mockEndPoint;

    @Test
    public void testUnMarshallMessage() throws Exception {

        mockEndPoint.expectedMessageCount(4);

        template.sendBody("direct:start", "'text1',',text2',1");
        template.sendBody("direct:start", "',',',text2',2");
        template.sendBody("direct:start", "',','text2,',3");
        template.sendBody("direct:start", "'',',text2,',4");


        mockEndPoint.assertIsSatisfied();

        MyCsvRecord2 rc = mockEndPoint.getExchanges().get(0).getIn().getBody(MyCsvRecord2.class);
        assertEquals("text1", rc.getText1());
        assertEquals(",text2", rc.getText2());
        assertEquals(BigDecimal.valueOf(1), rc.getNumber());

        rc = mockEndPoint.getExchanges().get(1).getIn().getBody(MyCsvRecord2.class);
        assertEquals(",", rc.getText1());
        assertEquals(",text2", rc.getText2());
        assertEquals(BigDecimal.valueOf(2), rc.getNumber());

        rc = mockEndPoint.getExchanges().get(2).getIn().getBody(MyCsvRecord2.class);
        assertEquals(",", rc.getText1());
        assertEquals("text2,", rc.getText2());
        assertEquals(BigDecimal.valueOf(3), rc.getNumber());

        rc = mockEndPoint.getExchanges().get(3).getIn().getBody(MyCsvRecord2.class);
        assertEquals(null, rc.getText1());
        assertEquals(",text2,", rc.getText2());
        assertEquals(BigDecimal.valueOf(4), rc.getNumber());



    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                BindyCsvDataFormat camelDataFormat =
                  new BindyCsvDataFormat(MyCsvRecord2.class);
                from("direct:start").unmarshal(camelDataFormat).to("mock:result");
            }
        };
    }


}
