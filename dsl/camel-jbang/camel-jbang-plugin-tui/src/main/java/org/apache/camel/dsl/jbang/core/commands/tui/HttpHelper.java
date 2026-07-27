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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class HttpHelper {

    private HttpHelper() {
    }

    record HttpResult(int statusCode, long elapsed, List<String> headerLines, String body, String error) {
    }

    static HttpResult sendRequest(String url, String method, String body, List<FormHelper.HeaderEntry> headers) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            boolean hasBody = body != null && !body.isEmpty();
            HttpRequest.BodyPublisher bodyPublisher = hasBody
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody();

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .method(method, bodyPublisher);

            if (headers != null) {
                for (FormHelper.HeaderEntry he : headers) {
                    String k = he.keyInput().text().trim();
                    String v = he.valueInput().text();
                    if (!k.isEmpty()) {
                        reqBuilder.header(k, v);
                    }
                }
            }

            long start = System.currentTimeMillis();
            HttpResponse<String> response = client.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;

            int statusCode = response.statusCode();

            List<String> headerLines = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : response.headers().map().entrySet()) {
                String k = entry.getKey();
                if (k == null || k.startsWith(":")) {
                    continue;
                }
                for (String v : entry.getValue()) {
                    headerLines.add(k + ": " + v);
                }
            }

            String responseBody = response.body();
            if (responseBody != null && responseBody.isEmpty()) {
                responseBody = null;
            }

            return new HttpResult(statusCode, elapsed, headerLines, responseBody, null);
        } catch (Exception e) {
            String msg = e.getMessage();
            return new HttpResult(0, 0, List.of(), null, msg != null ? msg : e.getClass().getSimpleName());
        }
    }

    static String extractPlatformHttpPath(String fromUri) {
        String path = fromUri.substring("platform-http:".length());
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        while (path.startsWith("//")) {
            path = path.substring(1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    static String extractHttpMethod(String fromUri, String body) {
        int q = fromUri.indexOf('?');
        if (q >= 0) {
            String query = fromUri.substring(q + 1);
            for (String param : query.split("&")) {
                if (param.startsWith("httpMethodRestrict=")) {
                    String methods = param.substring("httpMethodRestrict=".length());
                    int comma = methods.indexOf(',');
                    return comma > 0 ? methods.substring(0, comma).trim() : methods.trim();
                }
            }
        }
        return (body != null && !body.isEmpty()) ? "POST" : "GET";
    }
}
