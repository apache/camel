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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;

import com.google.gson.Gson;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.GeolocationResult;
import org.apache.camel.CamelContext;
import org.apache.camel.test.junit5.CamelTestSupport;

public abstract class GeoCoderApiKeyTestBase extends CamelTestSupport {
    protected static final String CAMEL_GEOCODER_APIKEY = "CAMEL_GEOCODER_APIKEY";
    // Google Maps API KEY always starts with AIza or the creation of Request while key not available will fail
    protected static final String ALTERNATIVE_APIKEY = "AIza_GOOGLE_API_KEY";

    protected String getApiKey() {
        final String apiKey = System.getenv(CAMEL_GEOCODER_APIKEY);
        return apiKey != null ? apiKey : ALTERNATIVE_APIKEY;
    }

    protected Object getFromFile(String file, Class type) throws FileNotFoundException {
        BufferedReader br = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream(file)));
        return new Gson().fromJson(br, type);
    }

    protected GeolocationResult geolocateResult() throws FileNotFoundException {
        return (GeolocationResult) getFromFile("/result/geolocate.json", GeolocationResult.class);
    }

    protected GeocodingResult[] geocodeResult(String file) throws FileNotFoundException {
        return (GeocodingResult[]) getFromFile(file, GeocodingResult[].class);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        if (ALTERNATIVE_APIKEY.equals(getApiKey())) {
            context.addComponent("geocoder", createComponent());
        }
        return context;
    }

    protected abstract GeoCoderComponent createComponent() throws Exception;

}
