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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.format.factories.DefaultFactoryRegistry;
import org.apache.camel.dataformat.bindy.model.car.Car;
import org.apache.camel.dataformat.bindy.model.car.Car.Colour;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

/**
 * @version
 */
public class BindyCarQuoteAndCommaDelimiterTest extends CamelTestSupport {

    private static final String HEADER = "\"stockid\";\"make\";\"model\";\"deriv\";\"series\";\"registration\";\"chassis\";\"engine\";\"year\""
            + ";\"klms\";\"body\";\"colour\";\"enginesize\";\"trans\";\"fuel\";\"options\";\"desc\";\"status\";\"Reserve_price\";\"nvic\"";
    private static final String ROW = "\"SS552\";\"TOYOTA\";\"KLUGER\";\"CV 4X4\";\"MCU28R UPGRADE\";\"TBA\";\"\";\"\";\"2005\";\"155000.0\";\"4D WAGON\""
            + ";\"BLACK\";\"3.3 LTR\";\"5 Sp Auto\";\"MULTI POINT FINJ\";\"POWER MIRRORS, POWER STEERING, POWER WINDOWS, CRUISE CONTROL,"
            + " ENGINE IMMOBILISER, BRAKE ASSIST, DUAL AIRBAG PACKAGE, ANTI-LOCK BRAKING, CENTRAL LOCKING REMOTE CONTROL, ALARM SYSTEM/REMOTE"
            + " ANTI THEFT, AUTOMATIC AIR CON / CLIMATE CONTROL, ELECTRONIC BRAKE FORCE DISTRIBUTION, CLOTH TRIM, LIMITED SLIP DIFFERENTIAL,"
            + " RADIO CD WITH 6 SPEAKERS\";\"Dual Airbag Package, Anti-lock Braking, Automatic Air Con / Climate Control, Alarm System/Remote"
            + " Anti Theft, Brake Assist, Cruise Control, Central Locking Remote Control, Cloth Trim, Electronic Brake Force Distribution,"
            + " Engine Immobiliser, Limited Slip Differential, Power Mirrors, Power Steering, Power Windows, Radio CD with 6 Speakers"
            + " CV GOOD KLMS AUTO POWER OPTIONS GOOD KLMS   \";\"Used\";\"0.0\";\"EZR05I\"\n";

    @Before
    public void setup() {
        PropertyPlaceholderDelegateRegistry registry = (PropertyPlaceholderDelegateRegistry)context.getRegistry();
        JndiRegistry reg = (JndiRegistry)registry.getRegistry();
        reg.bind("defaultFactoryRegistry", new DefaultFactoryRegistry());
    }

    @Test
    public void testBindyUnmarshalQuoteAndCommaDelimiter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:out");
        mock.expectedMessageCount(1);

        template.sendBody("direct:out", HEADER + "\n" + ROW);

        assertMockEndpointsSatisfied();
        
        Car rec1 = mock.getReceivedExchanges().get(0).getIn().getBody(Car.class);

        assertEquals("SS552", rec1.getStockid());
        assertEquals("TOYOTA", rec1.getMake());
        assertEquals("KLUGER", rec1.getModel());
        assertEquals(2005, rec1.getYear());
        assertEquals(Double.valueOf("155000.0"), rec1.getKlms(), 0.0001);
        assertEquals("EZR05I", rec1.getNvic());
        assertEquals("Used", rec1.getStatus());
        assertEquals(Car.Colour.BLACK, rec1.getColour());
    }

    @Test
    public void testBindyMarshalQuoteAndCommaDelimiter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:in");
        mock.expectedMessageCount(1);

        Car car = getCar();

        template.sendBody("direct:in", car);

        assertMockEndpointsSatisfied();

        String body = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertEquals(ROW, body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                Class<?> type = org.apache.camel.dataformat.bindy.model.car.Car.class;
                BindyCsvDataFormat dataFormat = new BindyCsvDataFormat();
                dataFormat.setClassType(type);
                dataFormat.setLocale("en");

                from("direct:out")
                    .unmarshal().bindy(BindyType.Csv, type)
                    .to("mock:out");
                from("direct:in")
                    .marshal(dataFormat)
                    .to("mock:in");
            }
        };
    }

    private Car getCar() {
        Car car = new Car();
        car.setStockid("SS552");
        car.setMake("TOYOTA");
        car.setModel("KLUGER");
        car.setDeriv("CV 4X4");
        car.setSeries("MCU28R UPGRADE");
        car.setRegistration("TBA");
        car.setChassis("");
        car.setEngine("");
        car.setYear(2005);
        car.setKlms(155000);
        car.setBody("4D WAGON");
        car.setColour(Colour.BLACK);
        car.setEnginesize("3.3 LTR");
        car.setTrans("5 Sp Auto");
        car.setFuel("MULTI POINT FINJ");
        car.setOptions("POWER MIRRORS, POWER STEERING, POWER WINDOWS, CRUISE CONTROL,"
                + " ENGINE IMMOBILISER, BRAKE ASSIST, DUAL AIRBAG PACKAGE, ANTI-LOCK BRAKING, CENTRAL LOCKING REMOTE CONTROL, ALARM SYSTEM/REMOTE"
                + " ANTI THEFT, AUTOMATIC AIR CON / CLIMATE CONTROL, ELECTRONIC BRAKE FORCE DISTRIBUTION, CLOTH TRIM, LIMITED SLIP DIFFERENTIAL,"
                + " RADIO CD WITH 6 SPEAKERS");
        car.setDesc("Dual Airbag Package, Anti-lock Braking, Automatic Air Con / Climate Control, Alarm System/Remote"
                + " Anti Theft, Brake Assist, Cruise Control, Central Locking Remote Control, Cloth Trim, Electronic Brake Force Distribution,"
                + " Engine Immobiliser, Limited Slip Differential, Power Mirrors, Power Steering, Power Windows, Radio CD with 6 Speakers"
                + " CV GOOD KLMS AUTO POWER OPTIONS GOOD KLMS   ");
        car.setStatus("Used");
        car.setNvic("EZR05I");
        return car;
    }

}
