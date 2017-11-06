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
package org.apache.camel.component.yql.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.apache.camel.component.yql.configuration.YqlConfiguration;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YqlClient {

    private static final Logger LOG = LoggerFactory.getLogger(YqlClient.class);

    private final CloseableHttpClient httpClient;

    public YqlClient(final CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public YqlResponse get(final YqlConfiguration yqlConfiguration) throws Exception {

        final URI uri = new URIBuilder()
                .setScheme(yqlConfiguration.isHttps() ? "https" : "http")
                .setHost("query.yahooapis.com")
                .setPath("/v1/public/yql")
                .setParameters(buildParameters(yqlConfiguration))
                .build();

        LOG.debug("YQL query: {}", uri);

        final HttpGet httpget = new HttpGet(uri);
        try (final CloseableHttpResponse response = httpClient.execute(httpget)) {
            final YqlResponse yqlResponse = YqlResponse.builder()
                    .httpRequest(uri.toString())
                    .status(response.getStatusLine().getStatusCode())
                    .body(EntityUtils.toString(response.getEntity()))
                    .build();
            LOG.debug("YQL response: {}", yqlResponse.getBody());
            return yqlResponse;
        }
    }

    private List<NameValuePair> buildParameters(final YqlConfiguration yqlConfiguration) {
        final List<NameValuePair> nameValuePairs = new ArrayList<>();
        nameValuePairs.add(new BasicNameValuePair("q", yqlConfiguration.getQuery()));
        nameValuePairs.add(new BasicNameValuePair("format", yqlConfiguration.getFormat()));
        String callback = yqlConfiguration.getCallback();
        if (callback == null) {
            callback = "";
        }
        nameValuePairs.add(new BasicNameValuePair("callback", callback));
        if (yqlConfiguration.getCrossProduct() != null) {
            nameValuePairs.add(new BasicNameValuePair("crossProduct", yqlConfiguration.getCrossProduct()));
        }
        nameValuePairs.add(new BasicNameValuePair("diagnostics", Boolean.toString(yqlConfiguration.isDiagnostics())));
        nameValuePairs.add(new BasicNameValuePair("debug", Boolean.toString(yqlConfiguration.isDebug())));
        if (yqlConfiguration.getEnv() != null) {
            nameValuePairs.add(new BasicNameValuePair("env", yqlConfiguration.getEnv()));
        }
        if (yqlConfiguration.getJsonCompat() != null) {
            nameValuePairs.add(new BasicNameValuePair("jsonCompat", yqlConfiguration.getJsonCompat()));
        }
        return nameValuePairs;
    }
}