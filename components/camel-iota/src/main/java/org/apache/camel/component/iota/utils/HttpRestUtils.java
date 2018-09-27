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
package org.apache.camel.component.iota.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

public final class HttpRestUtils {

    private HttpRestUtils() {
    }

    private static String doHttp(final String url, final Map<String, Object> payload) throws ParseException, IOException {
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost request = new HttpPost(url);
        request.addHeader("Content-Type", "application/json");
        request.addHeader("X-IOTA-API-Version", "1");

        ObjectMapper mapper = new ObjectMapper();
        request.setEntity(new StringEntity(mapper.writeValueAsString(payload)));

        HttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();

        // use org.apache.http.util.EntityUtils to read json as string
        return EntityUtils.toString(entity, StandardCharsets.UTF_8);
    }

    public static String getTransfer(final String url, final String[] addresses, final String[] tags) throws ParseException, IOException {
        final Map<String, Object> payload = new HashMap<>();
        payload.put("command", "findTransactions");
        if (addresses != null) {
            payload.put("addresses", addresses);
        }
        if (tags != null) {
            payload.put("tags", tags);
        }

        return HttpRestUtils.doHttp(url, payload);
    }

    public static String getTrytes(final String url, final String hash) throws ParseException, IOException {
        final Map<String, Object> payload = new HashMap<>();
        payload.put("command", "getTrytes");
        payload.put("hashes", new String[] {hash});

        return HttpRestUtils.doHttp(url, payload);
    }
}
