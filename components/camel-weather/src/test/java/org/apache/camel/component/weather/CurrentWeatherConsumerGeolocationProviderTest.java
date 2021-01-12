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
package org.apache.camel.component.weather;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CurrentWeatherConsumerGeolocationProviderTest extends CamelTestSupport {

    private static final String APPID = "test";
    private static final String GEOLOCATION_ACCESS_KEY = "IPSTACK_ACCESS_KEY";
    private static final String GEOLOCATION_REQUEST_HOST_IP = "LOCAL_IP";

    @Test
    public void checkGeolocationProviderConfig() {
        WeatherEndpoint endpoint = context().getEndpoint("weather:foo?"
                                                         + "geolocationRequestHostIP=" + GEOLOCATION_REQUEST_HOST_IP
                                                         + "&geolocationAccessKey=" + GEOLOCATION_ACCESS_KEY
                                                         + "&appid=" + APPID,
                WeatherEndpoint.class);

        WeatherConfiguration configuration = endpoint.getConfiguration();
        assertEquals(APPID, configuration.getAppid());
        assertEquals(GEOLOCATION_ACCESS_KEY, configuration.getGeolocationAccessKey());
        assertEquals(GEOLOCATION_REQUEST_HOST_IP, configuration.getGeolocationRequestHostIP());
        assertNotNull(configuration.getGeoLocationProvider());
    }

}
