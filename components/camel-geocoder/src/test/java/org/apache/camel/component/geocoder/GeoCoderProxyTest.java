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
package org.apache.camel.component.geocoder;

import com.google.code.geocoder.Geocoder;
import com.google.code.geocoder.model.GeocodeResponse;
import com.google.code.geocoder.model.GeocoderRequest;
import com.google.code.geocoder.model.LatLng;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("This test should be executed manually")
public class GeoCoderProxyTest extends CamelTestSupport {

    @Test
    public void testGeoCoder() throws Exception {
        GeoCoderEndpoint endpoint = context.getEndpoint(
            "geocoder:address:current?headersOnly=true&proxyHost=localhost&proxyPort=3128&proxyAuthMethod=Basic&proxyAuthUsername=proxy&proxyAuthPassword=proxy",
            GeoCoderEndpoint.class);

        Geocoder geocoder = endpoint.createGeocoder();
        GeocoderRequest req = new GeocoderRequest();
        req.setLocation(new LatLng("45.4643", "9.1895"));
        GeocodeResponse res = geocoder.geocode(req);

        log.info("Response {} ", res);
    }
}
