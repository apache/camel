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

import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.GeocodingApiRequest;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("This test should be executed manually with a proxy set up")
public class GeoCoderProxyTest extends GeoCoderApiKeyTestBase {

    @Test
    public void testGeoCoderProxyNoAuth() throws Exception {
        GeoCoderEndpoint endpoint = context.
                getEndpoint("geocoder:address:current?headersOnly=true&proxyHost=localhost"
                        + "&proxyPort=8888&apiKey=" + getApiKey(),
                             GeoCoderEndpoint.class);

        GeoApiContext context = endpoint.createGeoApiContext();
        GeocodingApiRequest geocodingApiRequest = GeocodingApi.reverseGeocode(context, new LatLng(45.4643, 9.1895));
        GeocodingResult[] results = geocodingApiRequest.await();

        log.info("Response {}", results);
    }

    @Test
    public void testGeoCoderWithAuth() throws Exception {
        GeoCoderEndpoint endpoint = context.
                getEndpoint("geocoder:address:current?headersOnly=true&proxyHost=localhost"
                       + "&proxyPort=8888&proxyAuthMethod=Basic&proxyAuthUsername=proxy&proxyAuthPassword=proxy&apiKey=" + getApiKey(),
                             GeoCoderEndpoint.class);

        GeoApiContext context = endpoint.createGeoApiContext();
        GeocodingApiRequest geocodingApiRequest = GeocodingApi.reverseGeocode(context, new LatLng(45.4643, 9.1895));
        GeocodingResult[] results = geocodingApiRequest.await();

        log.info("Response {}", results);
    }

}
