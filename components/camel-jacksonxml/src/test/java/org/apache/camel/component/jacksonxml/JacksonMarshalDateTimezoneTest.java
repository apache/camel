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
package org.apache.camel.component.jacksonxml;

import static java.lang.String.format;
import static org.junit.Assume.assumeTrue;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JacksonMarshalDateTimezoneTest extends CamelTestSupport {

    @Test
    public void testMarshalDate() throws Exception {
        GregorianCalendar in = new GregorianCalendar(2017, Calendar.APRIL, 25, 17, 0, 10);

        MockEndpoint mock = getMockEndpoint("mock:result");

        Object marshalled = template.requestBody("direct:in", in.getTime());
        String marshalledAsString = context.getTypeConverter().convertTo(String.class, marshalled);
        assertEquals("<Date>1493132410000</Date>", marshalledAsString);

        mock.expectedMessageCount(1);

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                JacksonXMLDataFormat format = new JacksonXMLDataFormat();
                String timeZoneId = "Africa/Ouagadougou";
                TimeZone timeZone = TimeZone.getTimeZone(timeZoneId);
                assumeTrue(format("TimeZone with id '%s' is available", timeZoneId), timeZone.getID().equals(timeZoneId));
                format.setTimezone(timeZone);

                from("direct:in").marshal(format).to("mock:result");
            }
        };
    }

}
