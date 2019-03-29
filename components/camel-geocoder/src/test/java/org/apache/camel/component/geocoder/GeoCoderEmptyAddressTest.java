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
import org.junit.Test;

public class GeoCoderEmptyAddressTest extends GeoCoderApiKeyTestBase {

    @Test
    public void testGeoCoder() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        mock.expectedHeaderReceived(GeoCoderConstants.STATUS, GeocoderStatus.ZERO_RESULTS);
        // the address header overrides the endpoint configuration
        template.sendBodyAndHeader("direct:start", "Hello", GeoCoderConstants.ADDRESS, " ");
        
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .to("geocoder:address: ?apiKey=" + getApiKey())
                    .to("log:result")
                    .log("Location ${header.CamelGeocoderAddress} is at lat/lng: ${header.CamelGeocoderLatlng} in city ${header.CamelGeocoderCity}")
                    .to("mock:result");
            }
        };
    }
}
