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

import java.io.IOException;

import com.google.maps.GeocodingApiRequest;
import com.google.maps.PendingResult;
import com.google.maps.errors.ApiException;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.GeolocationResult;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class GeocoderRequestWrapper {

    public GeocodingResult[] geocodingRequest(GeocodingApiRequest request)
            throws InterruptedException, ApiException, IOException {
        return request.await();
    }

    public GeolocationResult geolocationRequest(PendingResult<GeolocationResult> request)
            throws InterruptedException, ApiException, IOException {
        return request.await();
    }

    public String nominationRequest(HttpUriRequest request) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            try (CloseableHttpResponse resp = httpClient.execute(request)) {
                return EntityUtils.toString(resp.getEntity());
            }
        }
    }
}
