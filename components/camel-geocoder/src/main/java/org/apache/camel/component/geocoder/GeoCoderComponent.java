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

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Represents the component that manages {@link GeoCoderEndpoint}.
 */
@Component("geocoder")
public class GeoCoderComponent extends DefaultComponent {

    public GeoCoderComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining,
                                      Map<String, Object> parameters) throws Exception {
        GeoCoderEndpoint endpoint = new GeoCoderEndpoint(uri, this);

        // remaining can either be address or latlng
        if (remaining.startsWith("address:")) {
            String adr = remaining.substring(8);
            endpoint.setAddress(adr);
        } else if (remaining.startsWith("latlng:")) {
            String latlng = remaining.substring(7);
            endpoint.setLatlng(latlng);
        } else {
            throw new IllegalArgumentException("URI context path should be either address: or latlng:, was: " + remaining);
        }

        setProperties(endpoint, parameters);
        return endpoint;
    }
}
