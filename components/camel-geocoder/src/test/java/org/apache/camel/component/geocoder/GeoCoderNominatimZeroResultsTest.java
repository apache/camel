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
import org.junit.jupiter.api.Test;

public class GeoCoderNominatimZeroResultsTest extends GeoCoderNominatimTestSupport {

    @Test
    public void testAddressNotFound() throws Exception {
        // search answers an empty array when it finds nothing
        answer("search", "[]");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("[]");
        mock.expectedHeaderReceived(GeoCoderConstants.STATUS, GeocoderStatus.ZERO_RESULTS);

        template.sendBody("direct:address", "Test");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testLatLngNotFound() throws Exception {
        // reverse answers an error text when there is no place at the latitude and longitude, such as at sea
        answer("reverse", "{\"error\":\"Unable to geocode\"}");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(GeoCoderConstants.STATUS, GeocoderStatus.ZERO_RESULTS);

        template.sendBody("direct:latlng", "Test");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testErrorAnswer() throws Exception {
        answer("reverse", "{\"error\":{\"code\":400,\"message\":\"Parameter 'lat' must be a number.\"}}");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(GeoCoderConstants.STATUS, GeocoderStatus.ERROR);

        template.sendBody("direct:latlng", "Test");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:address")
                        .to("geocoder:address:nowhere?type=NOMINATIM&serverUrl=RAW(" + serverUrl() + ")")
                        .to("mock:result");

                from("direct:latlng")
                        .to("geocoder:latlng:0,-30?type=NOMINATIM&serverUrl=RAW(" + serverUrl() + ")")
                        .to("mock:result");
            }
        };
    }
}
