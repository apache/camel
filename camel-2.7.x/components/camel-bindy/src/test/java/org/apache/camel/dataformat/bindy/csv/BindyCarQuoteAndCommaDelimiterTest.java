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
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.model.car.Car;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class BindyCarQuoteAndCommaDelimiterTest extends CamelTestSupport {

    @Test
    public void testBindyQuoteAndCommaDelimiter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        String header = "\"stockid\";\"make\";\"model\";\"deriv\";\"series\";\"registration\";\"chassis\";\"engine\";\"year\""
                + ";\"klms\";\"body\";\"colour\";\"enginesize\";\"trans\";\"fuel\";\"options\";\"desc\";\"status\";\"Reserve_price\";\"nvic\"";
        String row = "\"SS552\";\"TOYOTA\";\"KLUGER\";\"CV 4X4\";\"MCU28R UPGRADE\";\"TBA\";\"\";\"\";\"2005\";\"155000\";\"4D WAGON\""
                + ";\"BLACK\";\"3.3 LTR\";\"5 Sp Auto\";\"MULTI POINT FINJ\";\"POWER MIRRORS, POWER STEERING, POWER WINDOWS, CRUISE CONTROL,"
                + " ENGINE IMMOBILISER, BRAKE ASSIST, DUAL AIRBAG PACKAGE, ANTI-LOCK BRAKING, CENTRAL LOCKING REMOTE CONTROL, ALARM SYSTEM/REMOTE"
                + " ANTI THEFT, AUTOMATIC AIR CON / CLIMATE CONTROL, ELECTRONIC BRAKE FORCE DISTRIBUTION, CLOTH TRIM, LIMITED SLIP DIFFERENTIAL,"
                + " RADIO CD WITH 6 SPEAKERS\";\"Dual Airbag Package, Anti-lock Braking, Automatic Air Con / Climate Control, Alarm System/Remote"
                + " Anti Theft, Brake Assist, Cruise Control, Central Locking Remote Control, Cloth Trim, Electronic Brake Force Distribution,"
                + " Engine Immobiliser, Limited Slip Differential, Power Mirrors, Power Steering, Power Windows, Radio CD with 6 Speakers"
                + " CV GOOD KLMS AUTO POWER OPTIONS GOOD KLMS   \";\"Used\";\"\";\"EZR05I\" ";

        template.sendBody("direct:start", header + "\n" + row);

        assertMockEndpointsSatisfied();

        Map map1 = (Map) mock.getReceivedExchanges().get(0).getIn().getBody(List.class).get(0);

        Car rec1 = (Car) map1.values().iterator().next();

        assertEquals("SS552", rec1.getStockid());
        assertEquals("TOYOTA", rec1.getMake());
        assertEquals("KLUGER", rec1.getModel());
        assertEquals(2005, rec1.getYear());
        assertEquals(Double.valueOf("155000.0"), rec1.getKlms());
        assertEquals("EZR05I", rec1.getNvic());
        assertEquals("Used", rec1.getStatus());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .unmarshal().bindy(BindyType.Csv, "org.apache.camel.dataformat.bindy.model.car")
                    .to("mock:result");
            }
        };
    }

}
