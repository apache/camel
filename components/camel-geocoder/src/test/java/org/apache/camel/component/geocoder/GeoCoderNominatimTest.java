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
package org.apache.camel.component.geocoder;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class GeoCoderNominatimTest extends CamelTestSupport {

    @Test
    public void testGeoCoder() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(GeoCoderConstants.COUNTRY_SHORT, "ES");
        mock.expectedHeaderReceived(GeoCoderConstants.CITY, "Sevilla");

        // the address header overrides the endpoint configuration
        template.sendBody("direct:start", "Test");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to(
                        "geocoder:latlng:37.38619,-5.99255?type=NOMINATIM&serverUrl=RAW(https://nominatim.openstreetmap.org)")
                        .to("log:result")
                        .log("Location ${header.CamelGeocoderAddress} is at lat/lng: ${header.CamelGeocoderLatlng}"
                             + " and in city ${header.CamelGeoCoderCity} in country ${header.CamelGeoCoderCountryLong}")
                        .to("mock:result");
            }
        };
    }
}
